package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.sentry.BaggageHeader;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.Instrumenter;
import io.sentry.MeasurementUnit;
import io.sentry.SentryDate;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanContext;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.TraceContext;
import io.sentry.TracesSamplingDecision;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This holds a strong reference to the OpenTelemetry span, preventing it from being garbage
 * collected.
 *
 * <p>IMPORTANT: Only use this carefully. Please read below.
 *
 * <p>This class should only be used in cases where Sentry SDK is used to create an OpenTelemetry
 * span under the hood that no one holds a reference to otherwise.
 *
 * <p>e.g. ITransaction transaction = Sentry.startTransaction(...) Sentry creates an OTel span under
 * the hood, but no one would reference it unless this class is used and returned to the user. By
 * doing this, we tie the OTel span to the returned Sentry span/transaction which the user can hold
 * on to.
 */
@ApiStatus.Internal
public final class OtelStrongRefSpanWrapper implements IOtelSpanWrapper {

  @SuppressWarnings("UnusedVariable")
  private final @NotNull Span otelSpan;

  private final @NotNull IOtelSpanWrapper delegate;

  public OtelStrongRefSpanWrapper(@NotNull Span otelSpan, @NotNull IOtelSpanWrapper delegate) {
    this.otelSpan = otelSpan;
    this.delegate = delegate;
  }

  @Override
  public void setTransactionName(@NotNull String name) {
    delegate.setTransactionName(name);
  }

  @Override
  public void setTransactionName(@NotNull String name, @NotNull TransactionNameSource nameSource) {
    delegate.setTransactionName(name, nameSource);
  }

  @Override
  public @Nullable TransactionNameSource getTransactionNameSource() {
    return delegate.getTransactionNameSource();
  }

  @Override
  public @Nullable String getTransactionName() {
    return delegate.getTransactionName();
  }

  @Override
  public @NotNull SentryId getTraceId() {
    return delegate.getTraceId();
  }

  @Override
  public @NotNull Map<String, Object> getData() {
    return delegate.getData();
  }

  @Override
  public @NotNull Map<String, MeasurementValue> getMeasurements() {
    return delegate.getMeasurements();
  }

  @Override
  public @Nullable Boolean isProfileSampled() {
    return delegate.isProfileSampled();
  }

  @Override
  public @NotNull IScopes getScopes() {
    return delegate.getScopes();
  }

  @Override
  public @NotNull Map<String, String> getTags() {
    return delegate.getTags();
  }

  @Override
  public @NotNull Context storeInContext(Context context) {
    return delegate.storeInContext(context);
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation) {
    return delegate.startChild(operation);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    return delegate.startChild(operation, description, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull SpanContext spanContext, @NotNull SpanOptions spanOptions) {
    return delegate.startChild(spanContext, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter) {
    return delegate.startChild(operation, description, timestamp, instrumenter);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    return delegate.startChild(operation, description, timestamp, instrumenter, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation, @Nullable String description) {
    return delegate.startChild(operation, description);
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return delegate.toSentryTrace();
  }

  @Override
  public @Nullable TraceContext traceContext() {
    return delegate.traceContext();
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    return delegate.toBaggageHeader(thirdPartyBaggageHeaders);
  }

  @Override
  public void finish() {
    delegate.finish();
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    delegate.finish(status);
  }

  @Override
  public void finish(@Nullable SpanStatus status, @Nullable SentryDate timestamp) {
    delegate.finish(status, timestamp);
  }

  @Override
  public void setOperation(@NotNull String operation) {
    delegate.setOperation(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return delegate.getOperation();
  }

  @Override
  public void setDescription(@Nullable String description) {
    delegate.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return delegate.getDescription();
  }

  @Override
  public void setStatus(@Nullable SpanStatus status) {
    delegate.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return delegate.getStatus();
  }

  @Override
  public void setThrowable(@Nullable Throwable throwable) {
    delegate.setThrowable(throwable);
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return delegate.getThrowable();
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return delegate.getSpanContext();
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {
    delegate.setTag(key, value);
  }

  @Override
  public @Nullable String getTag(@Nullable String key) {
    return delegate.getTag(key);
  }

  @Override
  public boolean isFinished() {
    return delegate.isFinished();
  }

  @Override
  public void setData(@Nullable String key, @Nullable Object value) {
    delegate.setData(key, value);
  }

  @Override
  public @Nullable Object getData(@Nullable String key) {
    return delegate.getData(key);
  }

  @Override
  public void setMeasurement(@NotNull String name, @NotNull Number value) {
    delegate.setMeasurement(name, value);
  }

  @Override
  public void setMeasurement(
      @NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit) {
    delegate.setMeasurement(name, value, unit);
  }

  @Override
  public boolean updateEndDate(@NotNull SentryDate date) {
    return delegate.updateEndDate(date);
  }

  @Override
  public @NotNull SentryDate getStartDate() {
    return delegate.getStartDate();
  }

  @Override
  public @Nullable SentryDate getFinishDate() {
    return delegate.getFinishDate();
  }

  @Override
  public boolean isNoOp() {
    return delegate.isNoOp();
  }

  @Override
  public void setContext(@Nullable String key, @Nullable Object context) {
    delegate.setContext(key, context);
  }

  @Override
  public @NotNull Contexts getContexts() {
    return delegate.getContexts();
  }

  @Override
  public @Nullable Boolean isSampled() {
    return delegate.isSampled();
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return delegate.getSamplingDecision();
  }

  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    return delegate.makeCurrent();
  }

  @ApiStatus.Internal
  @Override
  public @Nullable Attributes getOpenTelemetrySpanAttributes() {
    return delegate.getOpenTelemetrySpanAttributes();
  }

  @Override
  public boolean isRoot() {
    return delegate.isRoot();
  }

  @Override
  public @Nullable Span getOpenTelemetrySpan() {
    return delegate.getOpenTelemetrySpan();
  }
}
