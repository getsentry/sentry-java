package io.sentry;

import io.sentry.protocol.Message;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.transport.RateLimiter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sentry Client interface */
public interface ISentryClient {

  /**
   * Whether the client is enabled or not
   *
   * @return true if its enabled or false otherwise.
   */
  boolean isEnabled();

  /**
   * Capture the event
   *
   * @param event the event
   * @param scope An optional scope to be applied to the event.
   * @param hint SDK specific but provides high level information about the origin of the event.
   * @return The Id (SentryId object) of the event.
   */
  @NotNull
  SentryId captureEvent(@NotNull SentryEvent event, @Nullable IScope scope, @Nullable Hint hint);

  /** Flushes out the queue for up to timeout seconds and disable the client. */
  void close();

  /**
   * Flushes out the queue for up to timeout seconds and disable the client.
   *
   * @param isRestarting if true, avoids locking the main thread when finishing the queue.
   */
  void close(boolean isRestarting);

  /**
   * Flushes events queued up, but keeps the client enabled.
   *
   * @param timeoutMillis time in milliseconds
   */
  void flush(long timeoutMillis);

  /**
   * Captures the event.
   *
   * @param event the event
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureEvent(@NotNull SentryEvent event) {
    return captureEvent(event, null, null);
  }

  /**
   * Captures the event.
   *
   * @param event the event
   * @param scope An optional scope to be applied to the event.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable IScope scope) {
    return captureEvent(event, scope, null);
  }

  /**
   * Capture the event
   *
   * @param event the event
   * @param hint SDK specific but provides high level information about the origin of the event.
   * @return The Id (SentryId object) of the event.
   */
  default @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Hint hint) {
    return captureEvent(event, null, hint);
  }

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @param level The message level.
   * @param scope An optional scope to be applied to the event.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureMessage(
      @NotNull String message, @NotNull SentryLevel level, @Nullable IScope scope) {
    SentryEvent event = new SentryEvent();
    Message sentryMessage = new Message();
    sentryMessage.setFormatted(message);
    event.setMessage(sentryMessage);
    event.setLevel(level);

    return captureEvent(event, scope);
  }

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @param level The message level.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureMessage(@NotNull String message, @NotNull SentryLevel level) {
    return captureMessage(message, level, null);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureException(@NotNull Throwable throwable) {
    return captureException(throwable, null, null);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @param scope An optional scope to be applied to the event.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureException(
      @NotNull Throwable throwable, @Nullable IScope scope, @Nullable Hint hint) {
    SentryEvent event = new SentryEvent(throwable);
    return captureEvent(event, scope, hint);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureException(@NotNull Throwable throwable, @Nullable Hint hint) {
    return captureException(throwable, null, hint);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param scope An optional scope to be applied to the event.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureException(@NotNull Throwable throwable, @Nullable IScope scope) {
    return captureException(throwable, scope, null);
  }

  @NotNull
  SentryId captureReplayEvent(
      @NotNull SentryReplayEvent event, @Nullable IScope scope, @Nullable Hint hint);

  /**
   * Captures a manually created user feedback and sends it to Sentry.
   *
   * @param userFeedback The user feedback to send to Sentry.
   */
  void captureUserFeedback(@NotNull UserFeedback userFeedback);

  /**
   * Captures a session. This method transform a session to an envelope and forwards to
   * captureEnvelope
   *
   * @param hint SDK specific but provides high level information about the origin of the event
   * @param session the Session
   */
  void captureSession(@NotNull Session session, @Nullable Hint hint);

  /**
   * Captures a session. This method transform a session to an envelope and forwards to
   * captureEnvelope
   *
   * @param session the Session
   */
  default void captureSession(@NotNull Session session) {
    captureSession(session, null);
  }

  /**
   * Captures an envelope.
   *
   * @param envelope the SentryEnvelope to send.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  @Nullable
  SentryId captureEnvelope(@NotNull SentryEnvelope envelope, @Nullable Hint hint);

  /**
   * Captures an envelope.
   *
   * @param envelope the SentryEnvelope to send.
   * @return The Id (SentryId object) of the event
   */
  default @Nullable SentryId captureEnvelope(@NotNull SentryEnvelope envelope) {
    return captureEnvelope(envelope, null);
  }

  /**
   * Captures a transaction.
   *
   * @param transaction the {@link ITransaction} to send
   * @param scope An optional scope to be applied to the event.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  default SentryId captureTransaction(
      @NotNull SentryTransaction transaction, @Nullable IScope scope, @Nullable Hint hint) {
    return captureTransaction(transaction, null, scope, hint);
  }

  /**
   * Captures a transaction.
   *
   * @param transaction the {@link ITransaction} to send
   * @param scope An optional scope to be applied to the event.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  default SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable IScope scope,
      @Nullable Hint hint) {
    return captureTransaction(transaction, traceContext, scope, hint, null);
  }

  /**
   * Captures a transaction.
   *
   * @param transaction the {@link ITransaction} to send
   * @param traceContext the trace context
   * @param scope An optional scope to be applied to the event.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @param profilingTraceData An optional profiling trace data captured during the transaction
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  @ApiStatus.Internal
  SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable IScope scope,
      @Nullable Hint hint,
      @Nullable ProfilingTraceData profilingTraceData);

  /**
   * Captures a transaction without scope nor hint.
   *
   * @param transaction the {@link ITransaction} to send
   * @param traceContext the trace context
   * @return The Id (SentryId object) of the event
   */
  @ApiStatus.Internal
  default @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction, @Nullable TraceContext traceContext) {
    return captureTransaction(transaction, traceContext, null, null);
  }

  /**
   * Captures a transaction without scope nor hint.
   *
   * @param transaction the {@link ITransaction} to send
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureTransaction(@NotNull SentryTransaction transaction) {
    return captureTransaction(transaction, null, null, null);
  }

  /**
   * Captures the profile chunk and enqueues it for sending to Sentry server.
   *
   * @param profilingContinuousData the continuous profiling payload
   * @return the profile chunk id
   */
  @ApiStatus.Internal
  @NotNull
  SentryId captureProfileChunk(
      final @NotNull ProfileChunk profilingContinuousData, final @Nullable IScope scope);

  @NotNull
  @ApiStatus.Experimental
  SentryId captureCheckIn(@NotNull CheckIn checkIn, @Nullable IScope scope, @Nullable Hint hint);

  @ApiStatus.Experimental
  void captureLog(@NotNull SentryLogEvent logEvent, @Nullable IScope scope);

  @ApiStatus.Internal
  void captureBatchedLogEvents(@NotNull SentryLogEvents logEvents);

  @ApiStatus.Internal
  @Nullable
  RateLimiter getRateLimiter();

  @ApiStatus.Internal
  default boolean isHealthy() {
    return true;
  }
}
