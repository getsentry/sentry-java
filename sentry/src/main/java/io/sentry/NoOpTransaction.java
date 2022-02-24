package io.sentry;

import io.sentry.protocol.SentryId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpTransaction implements ITransaction {

  private static final NoOpTransaction instance = new NoOpTransaction();

  private NoOpTransaction() {}

  public static NoOpTransaction getInstance() {
    return instance;
  }

  @Override
  public void setName(@NotNull String name) {}

  @Override
  public @NotNull String getName() {
    return "";
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
  public @Nullable String getDescription() {
    return null;
  }

  @Override
  public @NotNull List<Span> getSpans() {
    return Collections.emptyList();
  }

  @Override
  public @Nullable Span getLatestActiveSpan() {
    return null;
  }

  @Override
  public @NotNull SentryId getEventId() {
    return SentryId.EMPTY_ID;
  }

  @Override
  public boolean isFinished() {
    return true;
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(SentryId.EMPTY_ID, SpanId.EMPTY_ID, false);
  }

  @Override
  public @NotNull TraceState traceState() {
    return new TraceState(SentryId.EMPTY_ID, "");
  }

  @Override
  public @NotNull TraceStateHeader toTraceStateHeader() {
    return new TraceStateHeader("");
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
  public @Nullable Boolean isSampled() {
    return null;
  }

  @Override
  public void setData(@NotNull String key, @NotNull Object value) {}

  @Override
  public @Nullable Object getData(@NotNull String key) {
    return null;
  }
}
