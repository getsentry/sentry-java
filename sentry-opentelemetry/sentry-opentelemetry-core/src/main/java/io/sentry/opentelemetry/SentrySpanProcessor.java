package io.sentry.opentelemetry;

import static io.sentry.TransactionContext.DEFAULT_TRANSACTION_NAME;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.sentry.Baggage;
import io.sentry.DsnUtil;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Instrumenter;
import io.sentry.PropagationContext;
import io.sentry.ScopesAdapter;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryLongDate;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanId;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated please use {@link OtelSentrySpanProcessor} instead.
 */
@Deprecated
public final class SentrySpanProcessor implements SpanProcessor {

  private static final String TRACE_ORIGN = "auto.otel";

  private final @NotNull List<SpanKind> spanKindsConsideredForSentryRequests =
      Arrays.asList(SpanKind.CLIENT, SpanKind.INTERNAL);
  private final @NotNull SpanDescriptionExtractor spanDescriptionExtractor =
      new SpanDescriptionExtractor();

  @SuppressWarnings("deprecation")
  private final @NotNull io.sentry.SentrySpanStorage spanStorage =
      io.sentry.SentrySpanStorage.getInstance();

  private final @NotNull IScopes scopes;

  public SentrySpanProcessor() {
    this(ScopesAdapter.getInstance());
  }

  SentrySpanProcessor(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void onStart(final @NotNull Context parentContext, final @NotNull ReadWriteSpan otelSpan) {
    if (!ensurePrerequisites(otelSpan)) {
      return;
    }

    final @NotNull TraceData traceData = getTraceData(otelSpan, parentContext);

    if (isSentryRequest(otelSpan)) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not forwarding OpenTelemetry span %s to Sentry as this looks like a span for a request to Sentry (trace %s).",
              traceData.getSpanId(),
              traceData.getTraceId());
      return;
    }
    final @Nullable ISpan sentryParentSpan =
        traceData.getParentSpanId() == null ? null : spanStorage.get(traceData.getParentSpanId());

    if (sentryParentSpan != null) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Creating Sentry child span for OpenTelemetry span %s (trace %s). Parent span is %s.",
              traceData.getSpanId(),
              traceData.getTraceId(),
              traceData.getParentSpanId());
      final @NotNull SentryDate startDate =
          new SentryLongDate(otelSpan.toSpanData().getStartEpochNanos());
      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(TRACE_ORIGN);
      final @NotNull ISpan sentryChildSpan =
          sentryParentSpan.startChild(
              otelSpan.getName(), otelSpan.getName(), startDate, Instrumenter.OTEL, spanOptions);
      spanStorage.store(traceData.getSpanId(), sentryChildSpan);
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Creating Sentry transaction for OpenTelemetry span %s (trace %s).",
              traceData.getSpanId(),
              traceData.getTraceId());
      final @NotNull String transactionName = otelSpan.getName();
      final @NotNull TransactionNameSource transactionNameSource = TransactionNameSource.CUSTOM;
      final @Nullable String op = otelSpan.getName();
      final SpanId spanId = new SpanId(traceData.getSpanId());

      final @NotNull TransactionContext transactionContext =
          traceData.getSentryTraceHeader() == null
              ? new TransactionContext(
                  new SentryId(traceData.getTraceId()), spanId, null, null, null)
              : TransactionContext.fromPropagationContext(
                  PropagationContext.fromHeaders(
                      traceData.getSentryTraceHeader(), traceData.getBaggage(), spanId));
      ;
      transactionContext.setName(transactionName);
      transactionContext.setTransactionNameSource(transactionNameSource);
      transactionContext.setOperation(op);
      transactionContext.setInstrumenter(Instrumenter.OTEL);

      TransactionOptions transactionOptions = new TransactionOptions();
      transactionOptions.setStartTimestamp(
          new SentryLongDate(otelSpan.toSpanData().getStartEpochNanos()));
      transactionOptions.setOrigin(TRACE_ORIGN);

      ISpan sentryTransaction = scopes.startTransaction(transactionContext, transactionOptions);
      spanStorage.store(traceData.getSpanId(), sentryTransaction);
    }
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(final @NotNull ReadableSpan otelSpan) {
    if (!ensurePrerequisites(otelSpan)) {
      return;
    }

    final @NotNull TraceData traceData = getTraceData(otelSpan, null);
    final @Nullable ISpan sentrySpan = spanStorage.removeAndGet(traceData.getSpanId());

    if (sentrySpan == null) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Unable to find Sentry span for OpenTelemetry span %s (trace %s). This may simply mean it is a Sentry request.",
              traceData.getSpanId(),
              traceData.getTraceId());
      return;
    }

    if (isSentryRequest(otelSpan)) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not forwarding OpenTelemetry span %s to Sentry as this looks like a span for a request to Sentry (trace %s).",
              traceData.getSpanId(),
              traceData.getTraceId());
      return;
    }

    if (sentrySpan instanceof ITransaction) {
      final @NotNull ITransaction sentryTransaction = (ITransaction) sentrySpan;
      updateTransactionWithOtelData(sentryTransaction, otelSpan);
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Finishing Sentry transaction %s for OpenTelemetry span %s (trace %s).",
              sentryTransaction.getEventId(),
              traceData.getSpanId(),
              traceData.getTraceId());
    } else {
      updateSpanWithOtelData(sentrySpan, otelSpan);
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Finishing Sentry span for OpenTelemetry span %s (trace %s). Parent span is %s.",
              traceData.getSpanId(),
              traceData.getTraceId(),
              traceData.getParentSpanId());
    }

    final @NotNull SpanStatus sentryStatus = mapOtelStatus(otelSpan);
    final @NotNull SentryDate endTimestamp =
        new SentryLongDate(otelSpan.toSpanData().getEndEpochNanos());
    sentrySpan.finish(sentryStatus, endTimestamp);
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  private boolean ensurePrerequisites(final @NotNull ReadableSpan otelSpan) {
    if (!hasSentryBeenInitialized()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not forwarding OpenTelemetry span to Sentry as Sentry has not yet been initialized.");
      return false;
    }

    final @NotNull Instrumenter instrumenter = scopes.getOptions().getInstrumenter();
    if (!Instrumenter.OTEL.equals(instrumenter)) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not forwarding OpenTelemetry span to Sentry as instrumenter has been set to %s.",
              instrumenter);
      return false;
    }

    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    if (!otelSpanContext.isValid()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not forwarding OpenTelemetry span to Sentry as the span is invalid.");
      return false;
    }

    return true;
  }

  @SuppressWarnings("deprecation")
  private boolean isSentryRequest(final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanKind kind = otelSpan.getKind();
    if (!spanKindsConsideredForSentryRequests.contains(kind)) {
      return false;
    }

    final @Nullable String httpUrl = otelSpan.getAttribute(UrlAttributes.URL_FULL);
    return DsnUtil.urlContainsDsnHost(scopes.getOptions(), httpUrl);
  }

  private @NotNull TraceData getTraceData(
      final @NotNull ReadableSpan otelSpan, final @Nullable Context parentContext) {
    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    final @NotNull String otelSpanId = otelSpanContext.getSpanId();
    final @NotNull String otelParentSpanIdMaybeInvalid =
        otelSpan.getParentSpanContext().getSpanId();
    final @NotNull String otelTraceId = otelSpanContext.getTraceId();
    final @Nullable String otelParentSpanId =
        io.opentelemetry.api.trace.SpanId.isValid(otelParentSpanIdMaybeInvalid)
            ? otelParentSpanIdMaybeInvalid
            : null;

    @Nullable SentryTraceHeader sentryTraceHeader = null;
    @Nullable Baggage baggage = null;

    if (parentContext != null) {
      sentryTraceHeader = parentContext.get(SentryOtelKeys.SENTRY_TRACE_KEY);
      if (sentryTraceHeader != null) {
        baggage = parentContext.get(SentryOtelKeys.SENTRY_BAGGAGE_KEY);
      }
    }

    return new TraceData(otelTraceId, otelSpanId, otelParentSpanId, sentryTraceHeader, baggage);
  }

  private void updateTransactionWithOtelData(
      final @NotNull ITransaction sentryTransaction, final @NotNull ReadableSpan otelSpan) {
    final @NotNull OtelSpanInfo otelSpanInfo =
        spanDescriptionExtractor.extractSpanInfo(otelSpan.toSpanData(), null);
    sentryTransaction.setOperation(otelSpanInfo.getOp());
    String transactionName = otelSpanInfo.getDescription();
    sentryTransaction.setName(
        transactionName == null ? DEFAULT_TRANSACTION_NAME : transactionName,
        otelSpanInfo.getTransactionNameSource());

    final @NotNull Map<String, Object> otelContext = toOtelContext(otelSpan);
    sentryTransaction.setContext("otel", otelContext);
  }

  private @NotNull Map<String, Object> toOtelContext(final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanData spanData = otelSpan.toSpanData();
    final @NotNull Map<String, Object> context = new HashMap<>();

    context.put("attributes", toMapWithStringKeys(spanData.getAttributes()));
    context.put("resource", toMapWithStringKeys(spanData.getResource().getAttributes()));

    return context;
  }

  private void updateSpanWithOtelData(
      final @NotNull ISpan sentrySpan, final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanData spanData = otelSpan.toSpanData();

    sentrySpan.setData("otel.kind", otelSpan.getKind());

    spanData
        .getAttributes()
        .forEach(
            (attributeKey, value) -> {
              if (value != null) {
                sentrySpan.setData(attributeKey.getKey(), value);
              }
            });

    final @NotNull OtelSpanInfo otelSpanInfo =
        spanDescriptionExtractor.extractSpanInfo(otelSpan.toSpanData(), null);
    sentrySpan.setOperation(otelSpanInfo.getOp());
    sentrySpan.setDescription(otelSpanInfo.getDescription());
  }

  @SuppressWarnings("deprecation")
  private SpanStatus mapOtelStatus(final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanData otelSpanData = otelSpan.toSpanData();
    final @NotNull StatusData otelStatus = otelSpanData.getStatus();
    final @NotNull StatusCode otelStatusCode = otelStatus.getStatusCode();

    if (StatusCode.OK.equals(otelStatusCode) || StatusCode.UNSET.equals(otelStatusCode)) {
      return SpanStatus.OK;
    }

    final @Nullable Long httpStatus =
        otelSpan.getAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE);
    if (httpStatus != null) {
      final @Nullable SpanStatus spanStatus = SpanStatus.fromHttpStatusCode(httpStatus.intValue());
      if (spanStatus != null) {
        return spanStatus;
      }
    }

    return SpanStatus.UNKNOWN_ERROR;
  }

  private boolean hasSentryBeenInitialized() {
    return scopes.isEnabled();
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
}
