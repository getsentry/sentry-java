package io.sentry.opentelemetry;

import io.sentry.BaggageHeader;
import io.sentry.Hint;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Instrumenter;
import io.sentry.MeasurementUnit;
import io.sentry.SentryDate;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanContext;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.TraceContext;
import io.sentry.TracesSamplingDecision;
import io.sentry.metrics.LocalMetricsAggregator;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OtelTransactionSpanForwarder implements ITransaction {

  private final @NotNull ISpan rootSpan;

  public OtelTransactionSpanForwarder(final @NotNull ISpan rootSpan) {
    this.rootSpan = Objects.requireNonNull(rootSpan, "root span is required");
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation) {
    return rootSpan.startChild(operation);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    return rootSpan.startChild(operation, description, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter) {
    return rootSpan.startChild(operation, description, timestamp, instrumenter);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    // TODO [POTEL]
    //    return rootSpan.startChild(operation, description, timestamp, spanOptions);
    return rootSpan.startChild(operation, description, timestamp, Instrumenter.SENTRY);
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation, @Nullable String description) {
    return rootSpan.startChild(operation, description);
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    // TODO [POTEL] root span?
    return rootSpan.toSentryTrace();
  }

  @Override
  public @Nullable TraceContext traceContext() {
    // TODO [POTEL] root span?
    return rootSpan.traceContext();
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    // TODO [POTEL] root span?
    return rootSpan.toBaggageHeader(thirdPartyBaggageHeaders);
  }

  @Override
  public void finish() {
    // TODO [POTEL] should this finish all spans?
    rootSpan.finish();
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    rootSpan.finish(status);
  }

  @Override
  public void finish(@Nullable SpanStatus status, @Nullable SentryDate timestamp) {
    rootSpan.finish(status, timestamp);
  }

  @Override
  public void setOperation(@NotNull String operation) {
    rootSpan.startChild(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return rootSpan.getOperation();
  }

  @Override
  public void setDescription(@Nullable String description) {
    rootSpan.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return rootSpan.getDescription();
  }

  @Override
  public void setStatus(@Nullable SpanStatus status) {
    rootSpan.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return rootSpan.getStatus();
  }

  @Override
  public void setThrowable(@Nullable Throwable throwable) {
    rootSpan.setThrowable(throwable);
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return rootSpan.getThrowable();
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return rootSpan.getSpanContext();
  }

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {
    rootSpan.setTag(key, value);
  }

  @Override
  public @Nullable String getTag(@NotNull String key) {
    return rootSpan.getTag(key);
  }

  @Override
  public boolean isFinished() {
    return rootSpan.isFinished();
  }

  @Override
  public void setData(@NotNull String key, @NotNull Object value) {
    rootSpan.setData(key, value);
  }

  @Override
  public @Nullable Object getData(@NotNull String key) {
    return rootSpan.getData(key);
  }

  @Override
  public void setMeasurement(@NotNull String name, @NotNull Number value) {
    rootSpan.setMeasurement(name, value);
  }

  @Override
  public void setMeasurement(
      @NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit) {
    rootSpan.setMeasurement(name, value, unit);
  }

  @Override
  public boolean updateEndDate(@NotNull SentryDate date) {
    return rootSpan.updateEndDate(date);
  }

  @Override
  public @NotNull SentryDate getStartDate() {
    return rootSpan.getStartDate();
  }

  @Override
  public @Nullable SentryDate getFinishDate() {
    return rootSpan.getFinishDate();
  }

  @Override
  public boolean isNoOp() {
    return rootSpan.isNoOp();
  }

  @Override
  public @Nullable LocalMetricsAggregator getLocalMetricsAggregator() {
    return rootSpan.getLocalMetricsAggregator();
  }

  @Override
  public @NotNull TransactionNameSource getTransactionNameSource() {
    return rootSpan.getNameSource();
  }

  @Override
  public @NotNull List<io.sentry.Span> getSpans() {
    // TODO [POTEL]
    return new ArrayList<>();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @Nullable SentryDate timestamp) {
    // TODO [POTEL]
    return rootSpan.startChild(operation, description, timestamp, Instrumenter.SENTRY);
  }

  @Override
  public @Nullable Boolean isSampled() {
    return rootSpan.isSampled();
  }

  @Override
  public @Nullable Boolean isProfileSampled() {
    // TODO [POTEL]
    return null;
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return rootSpan.getSamplingDecision();
  }

  @Override
  public @Nullable ISpan getLatestActiveSpan() {
    return rootSpan;
  }

  @Override
  public @NotNull SentryId getEventId() {
    return rootSpan.getEventId();
  }

  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    return rootSpan.makeCurrent();
  }

  @Override
  public void scheduleFinish() {
    // TODO [POTEL]
  }

  @Override
  public void forceFinish(
      @NotNull SpanStatus status, boolean dropIfNoChildren, @Nullable Hint hint) {
    // TODO [POTEL]
    rootSpan.finish(status);
  }

  @Override
  public void finish(
      @Nullable SpanStatus status,
      @Nullable SentryDate timestamp,
      boolean dropIfNoChildren,
      @Nullable Hint hint) {
    // TODO [POTEL]
    rootSpan.finish(status, timestamp);
  }

  @Override
  public void setContext(@NotNull String key, @NotNull Object context) {
    // TODO [POTEL] either set on root span or store in global storage or store on scopes
    // thoughts:
    // - span would have to save it on global storage too since we can't add complex data to otel
    // span
    // - with span ingestion there isn't a transaction anymore, so if we still need Contexts it
    // should go on the (root) span
    rootSpan.setContext(key, context);
  }

  @Override
  public @NotNull Contexts getContexts() {
    return rootSpan.getContexts();
  }

  @Override
  public void setName(@NotNull String name) {
    rootSpan.setName(name);
  }

  @Override
  public void setName(@NotNull String name, @NotNull TransactionNameSource nameSource) {
    rootSpan.setName(name, nameSource);
  }

  @Override
  public @NotNull TransactionNameSource getNameSource() {
    return rootSpan.getNameSource();
  }

  @Override
  public @NotNull String getName() {
    return rootSpan.getName();
  }
}