package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NoOpSentryClient implements ISentryClient {

  private static final NoOpSentryClient instance = new NoOpSentryClient();

  private NoOpSentryClient() {}

  public static NoOpSentryClient getInstance() {
    return instance;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, @Nullable Scope scope, @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public void close() {}

  @Override
  public void flush(long timeoutMillis) {}

  @Override
  public void captureUserFeedback(@NotNull UserFeedback userFeedback) {}

  @Override
  public void captureSession(@NotNull Session session, @Nullable Hint hint) {}

  @Override
  public SentryId captureEnvelope(@NotNull SentryEnvelope envelope, @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable Scope scope,
      @Nullable Hint hint,
      @Nullable ProfilingTraceData profilingTraceData) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureCheckIn(
      @NotNull CheckIn checkIn, @Nullable Scope scope, @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }
}
