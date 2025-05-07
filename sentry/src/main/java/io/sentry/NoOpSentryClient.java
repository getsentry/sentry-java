package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.transport.RateLimiter;
import org.jetbrains.annotations.ApiStatus;
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
      @NotNull SentryEvent event, @Nullable IScope scope, @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public void close(final boolean isRestarting) {}

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
      @Nullable IScope scope,
      @Nullable Hint hint,
      @Nullable ProfilingTraceData profilingTraceData) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureProfileChunk(
      final @NotNull ProfileChunk profileChunk, final @Nullable IScope scope) {
    return SentryId.EMPTY_ID;
  }

  @Override
  @ApiStatus.Experimental
  public @NotNull SentryId captureCheckIn(
      @NotNull CheckIn checkIn, @Nullable IScope scope, @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureReplayEvent(
      @NotNull SentryReplayEvent event, @Nullable IScope scope, @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }

  @ApiStatus.Experimental
  @Override
  public void captureLog(
      @NotNull SentryLogEvent logEvent, @Nullable IScope scope, @Nullable Hint hint) {
    // do nothing
  }

  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return null;
  }
}
