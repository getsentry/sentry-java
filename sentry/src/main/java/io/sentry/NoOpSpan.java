package io.sentry;

import io.sentry.protocol.SentryId;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
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
      @NotNull String operation,
      @Nullable String description,
      @Nullable Date timestamp,
      @NotNull Instrumenter instrumenter) {
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
  @ApiStatus.Internal
  public void finish(@Nullable SpanStatus status, @Nullable Date timestamp) {}

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
  public void setTag(@NotNull String key, @NotNull String value) {}

  @Override
  public @Nullable String getTag(@NotNull String key) {
    return null;
  }

  @Override
  public boolean isFinished() {
    return false;
  }

  @Override
  public void setData(@NotNull String key, @NotNull Object value) {}

  @Override
  public @Nullable Object getData(@NotNull String key) {
    return null;
  }

  @Override
  public void setMeasurement(@NotNull String name, @NotNull Number value) {}

  @Override
  public void setMeasurement(
      @NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit) {}

  @Override
  public boolean isNoOp() {
    return true;
  }
}
