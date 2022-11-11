package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.sentry.DateUtils;
import io.sentry.DsnUtil;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Instrumenter;
import io.sentry.Sentry;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentrySpanProcessor implements SpanProcessor {

  private final @NotNull Map<String, ISpan> spans = new ConcurrentHashMap<>();
  private final @NotNull List<SpanKind> spanKindsConsideredForSentryRequests =
      Arrays.asList(SpanKind.CLIENT, SpanKind.INTERNAL);
  private final @NotNull SpanDescriptionExtractor spanDescriptionExtractor =
      new SpanDescriptionExtractor();

  @Override
  public void onStart(final @NotNull Context parentContext, final @NotNull ReadWriteSpan otelSpan) {
    if (!hasSentryBeenInitialized()) {
      return;
    }

    if (!Instrumenter.OTEL.equals(Sentry.getCurrentHub().getOptions().getInstrumenter())) {
      return;
    }

    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    if (!otelSpanContext.isValid()) {
      return;
    }

    if (isSentryRequest(otelSpan)) {
      return;
    }

    final @NotNull TraceData traceData = getTraceData(otelSpan);
    final @Nullable ISpan sentryParentSpan =
        traceData.getParentSpanId() == null ? null : spans.get(traceData.getParentSpanId());

    if (sentryParentSpan != null) {
      System.out.println("found a parent span: " + traceData.getParentSpanId());
      final @NotNull Date startDate =
          DateUtils.nanosToDate(otelSpan.toSpanData().getStartEpochNanos());
      final @NotNull ISpan sentryChildSpan =
          sentryParentSpan.startChild(
              otelSpan.getName(), otelSpan.getName(), startDate, Instrumenter.OTEL);
      spans.put(traceData.getSpanId(), sentryChildSpan);
    } else {
      TransactionContext transactionContext =
          new TransactionContext(
              otelSpan.getName(),
              otelSpan.getName(),
              new SentryId(traceData.getTraceId()),
              new SpanId(traceData.getSpanId()),
              TransactionNameSource.CUSTOM,
              null,
              null,
              null);
      transactionContext.setInstrumenter(Instrumenter.OTEL);

      TransactionOptions transactionOptions = new TransactionOptions();
      transactionOptions.setStartTimestamp(
          DateUtils.nanosToDate(otelSpan.toSpanData().getStartEpochNanos()));

      ISpan sentryTransaction = Sentry.startTransaction(transactionContext, transactionOptions);
      spans.put(traceData.getSpanId(), sentryTransaction);
    }
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(final @NotNull ReadableSpan otelSpan) {
    if (!hasSentryBeenInitialized()) {
      return;
    }

    if (!Instrumenter.OTEL.equals(Sentry.getCurrentHub().getOptions().getInstrumenter())) {
      return;
    }

    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    if (!otelSpanContext.isValid()) {
      return;
    }

    final @NotNull TraceData traceData = getTraceData(otelSpan);
    final @Nullable ISpan sentrySpan = spans.remove(traceData.getSpanId());

    if (sentrySpan == null) {
      return;
    }

    if (isSentryRequest(otelSpan)) {
      return;
    }

    if (sentrySpan instanceof ITransaction) {
      ITransaction sentryTransaction = (ITransaction) sentrySpan;
      updateTransactionWithOtelData(sentryTransaction, otelSpan);
    } else {
      updateSpanWithOtelData(sentrySpan, otelSpan);
    }

    final @NotNull SpanStatus sentryStatus = mapOtelStatus(otelSpan);
    final @NotNull Date endTimestamp =
        DateUtils.nanosToDate(otelSpan.toSpanData().getEndEpochNanos());
    sentrySpan.finish(sentryStatus, endTimestamp);
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  private @NotNull TraceData getTraceData(final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    final @NotNull String otelSpanId = otelSpanContext.getSpanId();
    final @NotNull String otelParentSpanIdMaybeInvalid =
        otelSpan.getParentSpanContext().getSpanId();
    final @NotNull String otelTraceId = otelSpanContext.getTraceId();
    final @Nullable String otelParentSpanId =
        io.opentelemetry.api.trace.SpanId.isValid(otelParentSpanIdMaybeInvalid)
            ? otelParentSpanIdMaybeInvalid
            : null;

    // TODO read parentSampled and baggage from context set by propagator

    return new TraceData(otelTraceId, otelSpanId, otelParentSpanId);
  }

  private boolean isSentryRequest(final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanKind kind = otelSpan.getKind();
    if (!spanKindsConsideredForSentryRequests.contains(kind)) {
      return false;
    }

    final @Nullable String httpUrl = otelSpan.getAttribute(SemanticAttributes.HTTP_URL);
    return DsnUtil.urlContainsDsnHost(Sentry.getCurrentHub().getOptions(), httpUrl);
  }

  private void updateTransactionWithOtelData(
      final @NotNull ITransaction sentryTransaction, final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanDescription spanDescription =
        spanDescriptionExtractor.extractSpanDescription(otelSpan);
    sentryTransaction.setOperation(spanDescription.getOp());
    sentryTransaction.setName(
        spanDescription.getDescription(), spanDescription.getTransactionNameSource());

    final @NotNull Map<String, Object> otelContext = toOtelContext(otelSpan);
    System.out.println(otelContext);
    // TODO set otel context on transaction
  }

  private @NotNull Map<String, Object> toOtelContext(final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanData spanData = otelSpan.toSpanData();
    final @NotNull Map<String, Object> context = new HashMap<>();

    context.put("attributes", spanData.getAttributes().asMap());
    context.put("resource", spanData.getResource().getAttributes().asMap());

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

    final @NotNull SpanDescription spanDescription =
        spanDescriptionExtractor.extractSpanDescription(otelSpan);
    sentrySpan.setOperation(spanDescription.getOp());
    sentrySpan.setDescription(spanDescription.getDescription());
  }

  private SpanStatus mapOtelStatus(final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanData otelSpanData = otelSpan.toSpanData();
    final @NotNull StatusData otelStatus = otelSpanData.getStatus();
    final @NotNull StatusCode otelStatusCode = otelStatus.getStatusCode();

    if (StatusCode.OK.equals(otelStatusCode) || StatusCode.UNSET.equals(otelStatusCode)) {
      return SpanStatus.OK;
    }

    final @Nullable Long httpStatus = otelSpan.getAttribute(SemanticAttributes.HTTP_STATUS_CODE);
    if (httpStatus != null) {
      final @Nullable SpanStatus spanStatus = SpanStatus.fromHttpStatusCode(httpStatus.intValue());
      if (spanStatus != null) {
        return spanStatus;
      }
    }

    return SpanStatus.UNKNOWN_ERROR;
  }

  private boolean hasSentryBeenInitialized() {
    return Sentry.isEnabled();
  }
}
