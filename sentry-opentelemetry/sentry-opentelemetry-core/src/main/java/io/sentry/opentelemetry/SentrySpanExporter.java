package io.sentry.opentelemetry;

import static io.sentry.TransactionContext.DEFAULT_TRANSACTION_NAME;
import static io.sentry.opentelemetry.InternalSemanticAttributes.IS_REMOTE_PARENT;
import static io.sentry.opentelemetry.OtelInternalSpanDetectionUtil.isSentryRequest;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes;
import io.sentry.Baggage;
import io.sentry.DateUtils;
import io.sentry.DefaultSpanFactory;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Instrumenter;
import io.sentry.ScopeType;
import io.sentry.ScopesAdapter;
import io.sentry.SentryDate;
import io.sentry.SentryInstantDate;
import io.sentry.SentryLevel;
import io.sentry.SentryLongDate;
import io.sentry.SpanContext;
import io.sentry.SpanId;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentrySpanExporter implements SpanExporter {
  private volatile boolean stopped = false;
  private final List<SpanData> finishedSpans = new CopyOnWriteArrayList<>();
  private final @NotNull SentryWeakSpanStorage spanStorage = SentryWeakSpanStorage.getInstance();
  private final @NotNull SpanDescriptionExtractor spanDescriptionExtractor =
      new SpanDescriptionExtractor();
  private final @NotNull OpenTelemetryAttributesExtractor attributesExtractor =
      new OpenTelemetryAttributesExtractor();
  private final @NotNull IScopes scopes;

  private final @NotNull List<String> attributeKeysToRemove =
      Arrays.asList(
          InternalSemanticAttributes.IS_REMOTE_PARENT.getKey(),
          InternalSemanticAttributes.BAGGAGE.getKey(),
          InternalSemanticAttributes.BAGGAGE_MUTABLE.getKey(),
          InternalSemanticAttributes.SAMPLED.getKey(),
          InternalSemanticAttributes.SAMPLE_RATE.getKey(),
          InternalSemanticAttributes.PROFILE_SAMPLED.getKey(),
          InternalSemanticAttributes.PROFILE_SAMPLE_RATE.getKey(),
          InternalSemanticAttributes.PARENT_SAMPLED.getKey(),
          ProcessIncubatingAttributes.PROCESS_COMMAND_ARGS.getKey() // can be very long
          );
  private static final @NotNull Long SPAN_TIMEOUT = DateUtils.secondsToNanos(5 * 60);

  public static final String TRACE_ORIGIN = "auto.opentelemetry";

  public SentrySpanExporter() {
    this(ScopesAdapter.getInstance());
  }

  public SentrySpanExporter(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    if (stopped) {
      // TODO unsure if there's a way to attach a message
      return CompletableResultCode.ofFailure();
    }

    final int openSpanCount = finishedSpans.size();
    final int newSpanCount = spans.size();

    final @NotNull List<SpanData> nonSentryRequestSpans = filterOutSentrySpans(spans);

    finishedSpans.addAll(nonSentryRequestSpans);
    final @NotNull List<SpanData> remaining = maybeSend(finishedSpans);
    final int remainingSpanCount = remaining.size();
    final int sentSpanCount = openSpanCount + newSpanCount - remainingSpanCount;

    scopes
        .getOptions()
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "SpanExporter exported %s spans, %s unset spans remaining.",
            sentSpanCount,
            remainingSpanCount);

    this.finishedSpans.clear();

    final @NotNull SentryInstantDate now = new SentryInstantDate();

    final @NotNull List<SpanData> nonExpired =
        remaining.stream().filter((span) -> !isSpanTooOld(span, now)).collect(Collectors.toList());

    this.finishedSpans.addAll(nonExpired);

    // TODO

    return CompletableResultCode.ofSuccess();
  }

  private boolean isSpanTooOld(final @NotNull SpanData span, final @NotNull SentryInstantDate now) {
    final @NotNull SentryDate startDate = new SentryLongDate(span.getStartEpochNanos());
    boolean isTimedOut = now.diff(startDate) > SPAN_TIMEOUT;
    if (isTimedOut) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Dropping span %s as it was pending for too long.",
              span.getSpanId());
    }
    return isTimedOut;
  }

  private @NotNull List<SpanData> filterOutSentrySpans(final @NotNull Collection<SpanData> spans) {
    return spans.stream()
        .filter((span) -> !isSentryRequest(scopes, span.getKind(), span.getAttributes()))
        .collect(Collectors.toList());
  }

  private List<SpanData> maybeSend(final @NotNull List<SpanData> spans) {
    final @NotNull List<SpanNode> grouped = groupSpansWithParents(spans);
    final @NotNull List<SpanNode> remaining = new CopyOnWriteArrayList<>(grouped);
    final @NotNull List<SpanNode> rootNodes = findCompletedRootNodes(grouped);

    for (final @NotNull SpanNode rootNode : rootNodes) {
      remaining.remove(rootNode);
      final @Nullable SpanData span = rootNode.getSpan();
      if (span == null) {
        // TODO log
        continue;
      }
      final @Nullable ITransaction transaction = createTransactionForOtelSpan(span);
      if (transaction == null) {
        // TODO log
        continue;
      }

      for (final @NotNull SpanNode childNode : rootNode.getChildren()) {
        createAndFinishSpanForOtelSpan(childNode, transaction, remaining);
      }

      transaction.finish(
          mapOtelStatus(span, transaction), new SentryLongDate(span.getEndEpochNanos()));
    }

    return remaining.stream()
        .map((node) -> node.getSpan())
        .filter((it) -> it != null)
        .collect(Collectors.toList());
  }

  private void createAndFinishSpanForOtelSpan(
      final @NotNull SpanNode spanNode,
      final @NotNull ISpan parentSentrySpan,
      final @NotNull List<SpanNode> remaining) {
    remaining.remove(spanNode);
    final @Nullable SpanData spanData = spanNode.getSpan();

    // If this span should be dropped, we still want to create spans for the children of this
    if (spanData == null) {
      for (SpanNode childNode : spanNode.getChildren()) {
        createAndFinishSpanForOtelSpan(childNode, parentSentrySpan, remaining);
      }
      return;
    }

    final @NotNull String spanId = spanData.getSpanId();
    final @Nullable IOtelSpanWrapper sentrySpanMaybe =
        spanStorage.getSentrySpan(spanData.getSpanContext());
    final @NotNull OtelSpanInfo spanInfo =
        spanDescriptionExtractor.extractSpanInfo(spanData, sentrySpanMaybe);

    scopes
        .getOptions()
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "Creating Sentry child span for OpenTelemetry span %s (trace %s). Parent span is %s.",
            spanId,
            spanData.getTraceId(),
            spanData.getParentSpanId());
    final @NotNull SentryDate startDate = new SentryLongDate(spanData.getStartEpochNanos());
    final @NotNull SpanOptions spanOptions = new SpanOptions();
    final @NotNull io.sentry.SpanContext spanContext =
        parentSentrySpan
            .getSpanContext()
            .copyForChild(
                spanInfo.getOp(),
                parentSentrySpan.getSpanContext().getSpanId(),
                new SpanId(spanId));
    spanContext.setDescription(spanInfo.getDescription());
    spanContext.setInstrumenter(Instrumenter.SENTRY);
    if (sentrySpanMaybe != null) {
      spanContext.setSamplingDecision(sentrySpanMaybe.getSamplingDecision());
      spanOptions.setOrigin(sentrySpanMaybe.getSpanContext().getOrigin());
    } else {
      spanOptions.setOrigin(TRACE_ORIGIN);
    }

    spanOptions.setStartTimestamp(startDate);

    final @NotNull ISpan sentryChildSpan = parentSentrySpan.startChild(spanContext, spanOptions);

    for (Map.Entry<String, Object> dataField :
        toMapWithStringKeys(spanData.getAttributes()).entrySet()) {
      sentryChildSpan.setData(dataField.getKey(), dataField.getValue());
    }

    setOtelInstrumentationInfo(spanData, sentryChildSpan);
    setOtelSpanKind(spanData, sentryChildSpan);
    transferSpanDetails(sentrySpanMaybe, sentryChildSpan);

    for (SpanNode childNode : spanNode.getChildren()) {
      createAndFinishSpanForOtelSpan(childNode, sentryChildSpan, remaining);
    }

    sentryChildSpan.finish(
        mapOtelStatus(spanData, sentryChildSpan), new SentryLongDate(spanData.getEndEpochNanos()));
  }

  private void transferSpanDetails(
      final @Nullable IOtelSpanWrapper sourceSpanMaybe, final @NotNull ISpan targetSpan) {
    if (sourceSpanMaybe != null) {
      final @NotNull IOtelSpanWrapper sourceSpan = sourceSpanMaybe;

      final @NotNull Contexts contexts = sourceSpan.getContexts();
      targetSpan.getContexts().putAll(contexts);

      final @NotNull Map<String, Object> data = sourceSpan.getData();
      for (Map.Entry<String, Object> entry : data.entrySet()) {
        targetSpan.setData(entry.getKey(), entry.getValue());
      }

      final @NotNull Map<String, String> tags = sourceSpan.getTags();
      for (Map.Entry<String, String> entry : tags.entrySet()) {
        targetSpan.setTag(entry.getKey(), entry.getValue());
      }

      targetSpan.setStatus(sourceSpan.getStatus());
    }
  }

  private @Nullable ITransaction createTransactionForOtelSpan(final @NotNull SpanData span) {
    final @NotNull String spanId = span.getSpanId();
    final @NotNull String traceId = span.getTraceId();
    final @Nullable IOtelSpanWrapper sentrySpanMaybe =
        spanStorage.getSentrySpan(span.getSpanContext());
    final @Nullable IScopes scopesMaybe =
        sentrySpanMaybe != null ? sentrySpanMaybe.getScopes() : null;
    final @NotNull IScopes scopesToUseBeforeForking =
        scopesMaybe == null ? ScopesAdapter.getInstance() : scopesMaybe;
    final @NotNull IScopes scopesToUse =
        scopesToUseBeforeForking.forkedCurrentScope("SentrySpanExporter.createTransaction");
    final @NotNull OtelSpanInfo spanInfo =
        spanDescriptionExtractor.extractSpanInfo(span, sentrySpanMaybe);

    scopesToUse
        .getOptions()
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "Creating Sentry transaction for OpenTelemetry span %s (trace %s).",
            spanId,
            traceId);
    final SpanId sentrySpanId = new SpanId(spanId);

    @Nullable String transactionName = spanInfo.getDescription();
    @NotNull TransactionNameSource transactionNameSource = spanInfo.getTransactionNameSource();
    @Nullable SpanId parentSpanId = null;
    @Nullable Baggage baggage = null;

    if (sentrySpanMaybe != null) {
      final @NotNull IOtelSpanWrapper sentrySpan = sentrySpanMaybe;
      final @Nullable String transactionNameMaybe = sentrySpan.getTransactionName();
      if (transactionNameMaybe != null) {
        transactionName = transactionNameMaybe;
      }
      final @Nullable TransactionNameSource transactionNameSourceMaybe =
          sentrySpan.getTransactionNameSource();
      if (transactionNameSourceMaybe != null) {
        transactionNameSource = transactionNameSourceMaybe;
      }
      final @NotNull SpanContext spanContext = sentrySpan.getSpanContext();
      parentSpanId = spanContext.getParentSpanId();
      baggage = spanContext.getBaggage();
    }

    final @NotNull TransactionContext transactionContext =
        new TransactionContext(new SentryId(traceId), sentrySpanId, parentSpanId, null, baggage);

    TransactionOptions transactionOptions = new TransactionOptions();

    transactionContext.setName(
        transactionName == null ? DEFAULT_TRANSACTION_NAME : transactionName);
    transactionContext.setTransactionNameSource(transactionNameSource);
    transactionContext.setOperation(spanInfo.getOp());
    transactionContext.setInstrumenter(Instrumenter.SENTRY);
    if (sentrySpanMaybe != null) {
      transactionContext.setSamplingDecision(sentrySpanMaybe.getSamplingDecision());
      transactionOptions.setOrigin(sentrySpanMaybe.getSpanContext().getOrigin());
    }

    transactionOptions.setStartTimestamp(new SentryLongDate(span.getStartEpochNanos()));
    transactionOptions.setSpanFactory(new DefaultSpanFactory());

    ITransaction sentryTransaction =
        scopesToUse.startTransaction(transactionContext, transactionOptions);

    final @NotNull Map<String, Object> otelContext = toOtelContext(span);
    sentryTransaction.setContext("otel", otelContext);

    setOtelInstrumentationInfo(span, sentryTransaction);
    setOtelSpanKind(span, sentryTransaction);
    transferSpanDetails(sentrySpanMaybe, sentryTransaction);

    scopesToUse.configureScope(
        ScopeType.CURRENT,
        scope ->
            attributesExtractor.extract(span, sentryTransaction, scope, scopesToUse.getOptions()));

    return sentryTransaction;
  }

  private List<SpanNode> findCompletedRootNodes(final @NotNull List<SpanNode> grouped) {
    final @NotNull Predicate<SpanNode> isRootPredicate =
        (node) -> {
          return node.getParentNode() == null && node.getSpan() != null;
        };
    return grouped.stream().filter(isRootPredicate).collect(Collectors.toList());
  }

  private List<SpanNode> groupSpansWithParents(final @NotNull List<SpanData> spans) {
    final @NotNull Map<String, SpanNode> nodeMap = new HashMap<>();

    for (final @NotNull SpanData spanData : spans) {
      createOrUpdateSpanNodeAndRefs(nodeMap, spanData);
    }

    return nodeMap.values().stream().collect(Collectors.toList());
  }

  private void createOrUpdateSpanNodeAndRefs(
      final @NotNull Map<String, SpanNode> nodeMap, final @NotNull SpanData spanData) {
    final @NotNull String spanId = spanData.getSpanId();
    final String parentId = getParentId(spanData);
    if (parentId == null) {
      createOrUpdateNode(nodeMap, spanId, spanData, null, null);
      return;
    }

    final @NotNull SpanNode parentNode = createOrGetParentNode(nodeMap, parentId);
    final @NotNull SpanNode spanNode =
        createOrUpdateNode(nodeMap, spanId, spanData, null, parentNode);
    parentNode.addChild(spanNode);
  }

  private @Nullable String getParentId(final @NotNull SpanData spanData) {
    final @NotNull String parentSpanId = spanData.getParentSpanId();
    final @Nullable Boolean isRemoteParent = spanData.getAttributes().get(IS_REMOTE_PARENT);
    if (isRemoteParent != null && isRemoteParent) {
      return null;
    }
    if (io.opentelemetry.api.trace.SpanId.isValid(parentSpanId)) {
      return parentSpanId;
    }
    return null;
  }

  private @NotNull SpanNode createOrGetParentNode(
      final @NotNull Map<String, SpanNode> nodeMap, final @NotNull String spanId) {
    final @Nullable SpanNode existingNode = nodeMap.get(spanId);

    if (existingNode == null) {
      return createOrUpdateNode(nodeMap, spanId, null, null, null);
    }

    return existingNode;
  }

  // TODO do we ever pass children?
  private @NotNull SpanNode createOrUpdateNode(
      final @NotNull Map<String, SpanNode> nodeMap,
      final @NotNull String spanId,
      final @Nullable SpanData spanData,
      final @Nullable List<SpanNode> children,
      final @Nullable SpanNode parentNode) {
    final @Nullable SpanNode existingNode = nodeMap.get(spanId);

    if (existingNode != null) {
      final @Nullable SpanData existingNodeSpan = existingNode.getSpan();

      if (existingNodeSpan != null) {
        // If span is already set, nothing to do here
        return existingNode;
      }

      // If span is not set yet, we update it
      existingNode.setSpan(spanData);
      existingNode.setParentNode(parentNode);

      return existingNode;
    }

    final @NotNull SpanNode spanNode = new SpanNode(spanId);
    spanNode.setSpan(spanData);
    spanNode.setParentNode(parentNode);
    spanNode.addChildren(children);

    nodeMap.put(spanId, spanNode);

    return spanNode;
  }

  @SuppressWarnings("deprecation")
  private SpanStatus mapOtelStatus(
      final @NotNull SpanData otelSpanData, final @NotNull ISpan sentrySpan) {
    final @Nullable SpanStatus existingStatus = sentrySpan.getStatus();
    if (existingStatus != null && existingStatus != SpanStatus.UNKNOWN_ERROR) {
      return existingStatus;
    }

    final @NotNull StatusData otelStatus = otelSpanData.getStatus();
    final @NotNull StatusCode otelStatusCode = otelStatus.getStatusCode();

    if (StatusCode.OK.equals(otelStatusCode) || StatusCode.UNSET.equals(otelStatusCode)) {
      return SpanStatus.OK;
    }

    final @Nullable Long httpStatus =
        otelSpanData.getAttributes().get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE);
    if (httpStatus != null) {
      final @Nullable SpanStatus spanStatus = SpanStatus.fromHttpStatusCode(httpStatus.intValue());
      if (spanStatus != null) {
        return spanStatus;
      }
    }

    return SpanStatus.UNKNOWN_ERROR;
  }

  private @NotNull Map<String, Object> toOtelContext(final @NotNull SpanData spanData) {
    final @NotNull Map<String, Object> context = new HashMap<>();

    context.put("attributes", toMapWithStringKeys(spanData.getAttributes()));
    context.put("resource", toMapWithStringKeys(spanData.getResource().getAttributes()));

    return context;
  }

  private @NotNull Map<String, Object> toMapWithStringKeys(final @Nullable Attributes attributes) {
    final @NotNull Map<String, Object> mapWithStringKeys = new HashMap<>();

    if (attributes != null) {
      attributes.forEach(
          (key, value) -> {
            if (key != null) {
              final @NotNull String stringKey = key.getKey();
              if (!shouldRemoveAttribute(stringKey)) {
                mapWithStringKeys.put(stringKey, value);
              }
            }
          });
    }

    return mapWithStringKeys;
  }

  private boolean shouldRemoveAttribute(final @NotNull String key) {
    return attributeKeysToRemove.contains(key);
  }

  private void setOtelInstrumentationInfo(
      final @NotNull SpanData span, final @NotNull ISpan sentryTransaction) {
    final @Nullable String otelInstrumentationName = span.getInstrumentationScopeInfo().getName();
    if (otelInstrumentationName != null) {
      sentryTransaction.setData("otel.instrumentation.name", otelInstrumentationName);
    }

    final @Nullable String otelInstrumentationVersion =
        span.getInstrumentationScopeInfo().getVersion();
    if (otelInstrumentationVersion != null) {
      sentryTransaction.setData("otel.instrumentation.version", otelInstrumentationVersion);
    }
  }

  private void setOtelSpanKind(final @NotNull SpanData otelSpan, final @NotNull ISpan sentrySpan) {
    sentrySpan.setData("otel.kind", otelSpan.getKind().name());
  }

  @Override
  public CompletableResultCode flush() {
    scopes.flush(10000);
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    stopped = true;
    scopes.close();
    return CompletableResultCode.ofSuccess();
  }
}
