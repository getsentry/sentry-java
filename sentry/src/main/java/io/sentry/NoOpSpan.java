package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.SentryId;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpSpan implements ISpan {

  private static final NoOpSpan instance = new NoOpSpan();

  private NoOpSpan() {}

  public static NoOpSpan getInstance() {
    return instance;
  }

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull SpanContext spanContext, @NotNull SpanOptions spanOptions) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, final @Nullable String description) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(SentryId.EMPTY_ID, SpanId.EMPTY_ID, false);
  }

  @Override
  public @NotNull TraceContext traceContext() {
    return new TraceContext(SentryId.EMPTY_ID, "");
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    return null;
  }

  @Override
  public void finish() {}

  @Override
  public void finish(@Nullable SpanStatus status) {}

  @Override
  public void finish(@Nullable SpanStatus status, @Nullable SentryDate timestamp) {}

  @Override
  public void setOperation(@NotNull String operation) {}

  @Override
  public @NotNull String getOperation() {
    return "";
  }

  @Override
  public void setDescription(@Nullable String description) {}

  @Override
  public @Nullable String getDescription() {
    return null;
  }

  @Override
  public void setStatus(@Nullable SpanStatus status) {}

  @Override
  public @Nullable SpanStatus getStatus() {
    return null;
  }

  @Override
  public void setThrowable(@Nullable Throwable throwable) {}

  @Override
  public @Nullable Throwable getThrowable() {
    return null;
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return new SpanContext(SentryId.EMPTY_ID, SpanId.EMPTY_ID, "op", null, null);
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {}

  @Override
  public @Nullable String getTag(@Nullable String key) {
    return null;
  }

  @Override
  public boolean isFinished() {
    return false;
  }

  @Override
  public void setData(@Nullable String key, @Nullable Object value) {}

  @Override
  public @Nullable Object getData(@Nullable String key) {
    return null;
  }

  @Override
  public void setMeasurement(@NotNull String name, @NotNull Number value) {}

  @Override
  public void setMeasurement(
      @NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit) {}

  @Override
  public boolean updateEndDate(final @NotNull SentryDate date) {
    return false;
  }

  @Override
  public @NotNull SentryDate getStartDate() {
    return new SentryNanotimeDate();
  }

  @Override
  public @NotNull SentryDate getFinishDate() {
    return new SentryNanotimeDate();
  }

  @Override
  public boolean isNoOp() {
    return true;
  }

  @Override
  public void setContext(@Nullable String key, @Nullable Object context) {}

  @Override
  public @NotNull Contexts getContexts() {
    return new Contexts();
  }

  @Override
  public @Nullable Boolean isSampled() {
    return null;
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return null;
  }

  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {}
}
