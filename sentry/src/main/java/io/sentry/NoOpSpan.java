package io.sentry;

import io.sentry.protocol.SentryId;
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
      final @NotNull String operation, final @Nullable String description) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(SentryId.EMPTY_ID, SpanId.EMPTY_ID, false);
  }

  @Override
  public void finish() {}

  @Override
  public void finish(@Nullable SpanStatus status) {}

  @Override
  public void setOperation(@Nullable String operation) {}

  @Override
  public void setDescription(@Nullable String description) {}

  @Override
  public void setStatus(@Nullable SpanStatus status) {}

  @Override
  public void setThrowable(@Nullable Throwable throwable) {}

  @Override
  public @Nullable Throwable getThrowable() {
    return null;
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return new SpanContext(SentryId.EMPTY_ID, SpanId.EMPTY_ID, null, null);
  }

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {}
}
