package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.InternalSemanticAttributes.IS_REMOTE_PARENT;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.SemanticAttributes;
import io.sentry.DateUtils;
import io.sentry.DefaultSpanFactory;
import io.sentry.DsnUtil;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Instrumenter;
import io.sentry.ScopesAdapter;
import io.sentry.SentryDate;
import io.sentry.SentryInstantDate;
import io.sentry.SentryLevel;
import io.sentry.SentryLongDate;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.SentryId;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentrySpanExporter implements SpanExporter {
  private volatile boolean stopped = false;
  // TODO is a strong ref problematic here?
  // TODO [POTEL] a weak ref could mean spans are gone before we had a chance to attach them
  // somewhere
  private final List<SpanData> finishedSpans = new CopyOnWriteArrayList<>();
  private final @NotNull SentryWeakSpanStorage spanStorage = SentryWeakSpanStorage.getInstance();
  private final @NotNull SpanDescriptionExtractor spanDescriptionExtractor =
      new SpanDescriptionExtractor();
  private final @NotNull IScopes scopes;

  private final @NotNull List<SpanKind> spanKindsConsideredForSentryRequests =
      Arrays.asList(SpanKind.CLIENT, SpanKind.INTERNAL);
  private static final @NotNull Long SPAN_TIMEOUT = DateUtils.secondsToNanos(5 * 60);

  private static final String TRACE_ORIGN = "auto.potel";

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
        remaining.stream().filter((span) -> isSpanTooOld(span, now)).collect(Collectors.toList());
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
    return spans.stream().filter((span) -> !isSentryRequest(span)).collect(Collectors.toList());
  }

  @SuppressWarnings("deprecation")
  private boolean isSentryRequest(final @NotNull SpanData spanData) {
    final @NotNull SpanKind kind = spanData.getKind();
    if (!spanKindsConsideredForSentryRequests.contains(kind)) {
      return false;
    }

    final @Nullable String httpUrl = spanData.getAttributes().get(SemanticAttributes.HTTP_URL);
    if (DsnUtil.urlContainsDsnHost(scopes.getOptions(), httpUrl)) {
      return true;
    }

    final @Nullable String fullUrl = spanData.getAttributes().get(SemanticAttributes.URL_FULL);
    if (DsnUtil.urlContainsDsnHost(scopes.getOptions(), fullUrl)) {
      return true;
    }

    // TODO [POTEL] should check if enabled but multi init with different options makes testing hard
    // atm
    //    if (scopes.getOptions().isEnableSpotlight()) {
    final @Nullable String spotlightUrl = scopes.getOptions().getSpotlightConnectionUrl();
    if (spotlightUrl != null) {
      if (containsSpotlightUrl(fullUrl, spotlightUrl)) {
        return true;
      }
      if (containsSpotlightUrl(httpUrl, spotlightUrl)) {
        return true;
      }
    } else {
      if (containsSpotlightUrl(fullUrl, "http://localhost:8969/stream")) {
        return true;
      }
      if (containsSpotlightUrl(httpUrl, "http://localhost:8969/stream")) {
        return true;
      }
    }
    //    }

    return false;
  }

  private boolean containsSpotlightUrl(
      final @Nullable String requestUrl, final @NotNull String spotlightUrl) {
    if (requestUrl == null) {
      return false;
    }

    return requestUrl.toLowerCase(Locale.ROOT).contains(spotlightUrl.toLowerCase(Locale.ROOT));
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

      //      spanStorage.getScope()
      // transaction.finishWithScope
      transaction.finish(mapOtelStatus(span), new SentryLongDate(span.getEndEpochNanos()));
    }

    return remaining.stream()
        .map((node) -> node.getSpan())
        .filter((it) -> it != null)
        .collect(Collectors.toList());
  }

  private void createAndFinishSpanForOtelSpan(
      final @NotNull SpanNode spanNode,
      final @NotNull ISpan sentrySpan,
      final @NotNull List<SpanNode> remaining) {
    remaining.remove(spanNode);
    final @Nullable SpanData spanData = spanNode.getSpan();

    // If this span should be dropped, we still want to create spans for the children of this
    if (spanData == null) {
      for (SpanNode childNode : spanNode.getChildren()) {
        createAndFinishSpanForOtelSpan(childNode, sentrySpan, remaining);
      }
      return;
    }

    final @NotNull String spanId = spanData.getSpanId();
    final @NotNull OtelSpanInfo spanInfo = spanDescriptionExtractor.extractSpanInfo(spanData);
    // TODO attributes
    // TODO cleanup sentry attributes

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
    final @NotNull ISpan sentryChildSpan =
        sentrySpan.startChild(
            spanInfo.getOp(), spanInfo.getDescription(), startDate, Instrumenter.OTEL);

    sentryChildSpan.getSpanContext().setOrigin(TRACE_ORIGN);
    for (Map.Entry<String, Object> dataField : spanInfo.getDataFields().entrySet()) {
      sentryChildSpan.setData(dataField.getKey(), dataField.getValue());
    }

    for (SpanNode childNode : spanNode.getChildren()) {
      createAndFinishSpanForOtelSpan(childNode, sentryChildSpan, remaining);
    }

    sentryChildSpan.finish(
        mapOtelStatus(spanData), new SentryLongDate(spanData.getEndEpochNanos()));
  }

  private @Nullable ITransaction createTransactionForOtelSpan(final @NotNull SpanData span) {
    final @NotNull String spanId = span.getSpanId();
    final @NotNull String traceId = span.getTraceId();
    //    final @Nullable IScope scope = spanStorage.getScope(spanId);
    final @Nullable OtelSpanWrapper sentrySpanMaybe =
        spanStorage.getSentrySpan(span.getSpanContext());

    final @Nullable IScopes scopesMaybe =
        sentrySpanMaybe != null ? sentrySpanMaybe.getScopes() : null;
    final @NotNull IScopes scopesToUse =
        scopesMaybe == null ? ScopesAdapter.getInstance() : scopesMaybe;
    final @NotNull OtelSpanInfo spanInfo = spanDescriptionExtractor.extractSpanInfo(span);

    //    final @Nullable Boolean parentSampled =
    // span.getAttributes().get(InternalSemanticAttributes.PARENT_SAMPLED);
    // TODO DSC
    // TODO op, desc, tags, data, origin, source
    // TODO metadata

    // TODO we'll have to copy some of otel span attributes over to our transaction/span, e.g.
    // thread info is wrong because it's created here in the exporter

    scopesToUse
        .getOptions()
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "Creating Sentry transaction for OpenTelemetry span %s (trace %s).",
            spanId,
            traceId);
    final SpanId sentrySpanId = new SpanId(spanId);

    // TODO parentSpanId, parentSamplingDecision, baggage

    final @NotNull TransactionContext transactionContext =
        new TransactionContext(new SentryId(traceId), sentrySpanId, null, null, null);
    //      traceData.getSentryTraceHeader() == null
    //        ? new TransactionContext(
    //        new SentryId(traceData.getTraceId()), spanId, null, null, null)
    //        : TransactionContext.fromPropagationContext(
    //        PropagationContext.fromHeaders(
    //          traceData.getSentryTraceHeader(), traceData.getBaggage(), spanId));

    transactionContext.setName(spanInfo.getDescription());
    transactionContext.setTransactionNameSource(spanInfo.getTransactionNameSource());
    transactionContext.setOperation(spanInfo.getOp());
    transactionContext.setInstrumenter(Instrumenter.OTEL);

    TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setStartTimestamp(new SentryLongDate(span.getStartEpochNanos()));
    transactionOptions.setSpanFactory(new DefaultSpanFactory());

    ITransaction sentryTransaction =
        scopesToUse.startTransaction(transactionContext, transactionOptions);
    sentryTransaction.getSpanContext().setOrigin(TRACE_ORIGN);

    final @NotNull Map<String, Object> otelContext = toOtelContext(span);
    sentryTransaction.setContext("otel", otelContext);

    for (Map.Entry<String, Object> dataField : spanInfo.getDataFields().entrySet()) {
      sentryTransaction.setData(dataField.getKey(), dataField.getValue());
    }

    if (sentrySpanMaybe != null) {
      final @NotNull ISpan sentrySpan = sentrySpanMaybe;
      final @NotNull Contexts contexts = sentrySpan.getContexts();
      sentryTransaction.getContexts().putAll(contexts);
    }

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
  private SpanStatus mapOtelStatus(final @NotNull SpanData otelSpanData) {
    final @NotNull StatusData otelStatus = otelSpanData.getStatus();
    final @NotNull StatusCode otelStatusCode = otelStatus.getStatusCode();

    if (StatusCode.OK.equals(otelStatusCode) || StatusCode.UNSET.equals(otelStatusCode)) {
      return SpanStatus.OK;
    }

    final @Nullable Long httpStatus =
        otelSpanData.getAttributes().get(SemanticAttributes.HTTP_STATUS_CODE);
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
              mapWithStringKeys.put(key.getKey(), value);
            }
          });
    }

    return mapWithStringKeys;
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
