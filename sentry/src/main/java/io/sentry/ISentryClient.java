package io.sentry;

import io.sentry.protocol.Message;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
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
  SentryId captureEvent(@NotNull SentryEvent event, @Nullable Scope scope, @Nullable Object hint);

  /** Flushes out the queue for up to timeout seconds and disable the client. */
  void close();

  /**
   * Flushes events queued up, but keeps the client enabled. Not implemented yet.
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
  default @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Scope scope) {
    return captureEvent(event, scope, null);
  }

  /**
   * Capture the event
   *
   * @param event the event
   * @param hint SDK specific but provides high level information about the origin of the event.
   * @return The Id (SentryId object) of the event.
   */
  default @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Object hint) {
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
      @NotNull String message, @NotNull SentryLevel level, @Nullable Scope scope) {
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
      @NotNull Throwable throwable, @Nullable Scope scope, @Nullable Object hint) {
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
  default @NotNull SentryId captureException(@NotNull Throwable throwable, @Nullable Object hint) {
    return captureException(throwable, null, hint);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param scope An optional scope to be applied to the event.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureException(@NotNull Throwable throwable, @Nullable Scope scope) {
    return captureException(throwable, scope, null);
  }

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
  void captureSession(@NotNull Session session, @Nullable Object hint);

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
  SentryId captureEnvelope(@NotNull SentryEnvelope envelope, @Nullable Object hint);

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
      @NotNull SentryTransaction transaction, @Nullable Scope scope, @Nullable Object hint) {
    return captureTransaction(transaction, null, scope, hint);
  }

  /**
   * Captures a transaction.
   *
   * @param transaction the {@link ITransaction} to send
   * @param traceState the trace state
   * @param scope An optional scope to be applied to the event.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  @ApiStatus.Experimental
  SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceState traceState,
      @Nullable Scope scope,
      @Nullable Object hint);

  /**
   * Captures a transaction without scope nor hint.
   *
   * @param transaction the {@link ITransaction} to send
   * @param traceState the trace state
   * @return The Id (SentryId object) of the event
   */
  @ApiStatus.Experimental
  default @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction, @NotNull TraceState traceState) {
    return captureTransaction(transaction, traceState, null, null);
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
}
