package io.sentry;

import io.sentry.protocol.SentryId;
import java.util.Date;
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
      @NotNull String operation, @Nullable String description, @Nullable Date timestamp) {
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
  public @NotNull BaggageHeader toBaggageHeader() {
    return new BaggageHeader("");
  }

  @Override
  public void finish() {}

  @Override
  public void finish(@Nullable SpanStatus status) {}

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
}
