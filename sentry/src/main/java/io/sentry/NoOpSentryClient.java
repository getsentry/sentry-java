package io.sentry;

import io.sentry.hints.Hints;
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
      @NotNull SentryEvent event, @Nullable Scope scope, @Nullable Hints hints) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public void close() {}

  @Override
  public void flush(long timeoutMillis) {}

  @Override
  public void captureUserFeedback(@NotNull UserFeedback userFeedback) {}

  @Override
  public void captureSession(@NotNull Session session, @Nullable Hints hints) {}

  @Override
  public SentryId captureEnvelope(@NotNull SentryEnvelope envelope, @Nullable Hints hints) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceState traceState,
      @Nullable Scope scope,
      @Nullable Hints hints,
      @Nullable ProfilingTraceData profilingTraceData) {
    return SentryId.EMPTY_ID;
  }
}
