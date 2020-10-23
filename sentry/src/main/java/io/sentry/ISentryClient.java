package io.sentry;

import io.sentry.protocol.Message;
import io.sentry.protocol.SentryId;
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
  SentryId captureEvent(SentryEvent event, @Nullable Scope scope, @Nullable Object hint);

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
  default SentryId captureEvent(SentryEvent event) {
    return captureEvent(event, null, null);
  }

  /**
   * Captures the event.
   *
   * @param event the event
   * @param scope An optional scope to be applied to the event.
   * @return The Id (SentryId object) of the event
   */
  default SentryId captureEvent(SentryEvent event, @Nullable Scope scope) {
    return captureEvent(event, scope, null);
  }

  /**
   * Capture the event
   *
   * @param event the event
   * @param hint SDK specific but provides high level information about the origin of the event.
   * @return The Id (SentryId object) of the event.
   */
  default SentryId captureEvent(SentryEvent event, @Nullable Object hint) {
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
  default SentryId captureMessage(String message, SentryLevel level, @Nullable Scope scope) {
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
  default SentryId captureMessage(String message, SentryLevel level) {
    return captureMessage(message, level, null);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @return The Id (SentryId object) of the event
   */
  default SentryId captureException(Throwable throwable) {
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
  default SentryId captureException(
      Throwable throwable, @Nullable Scope scope, @Nullable Object hint) {
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
  default SentryId captureException(Throwable throwable, @Nullable Object hint) {
    return captureException(throwable, null, hint);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param scope An optional scope to be applied to the event.
   * @return The Id (SentryId object) of the event
   */
  default SentryId captureException(Throwable throwable, @Nullable Scope scope) {
    return captureException(throwable, scope, null);
  }

  /**
   * Captures a session. This method transform a session to an envelope and forwards to
   * captureEnvelope
   *
   * @param hint SDK specific but provides high level information about the origin of the event
   * @param session the Session
   */
  void captureSession(Session session, @Nullable Object hint);

  /**
   * Captures a session. This method transform a session to an envelope and forwards to
   * captureEnvelope
   *
   * @param session the Session
   */
  default void captureSession(Session session) {
    captureSession(session, null);
  }

  /**
   * Captures an envelope.
   *
   * @param envelope the SentryEnvelope to send.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  SentryId captureEnvelope(SentryEnvelope envelope, @Nullable Object hint);

  /**
   * Captures an envelope.
   *
   * @param envelope the SentryEnvelope to send.
   * @return The Id (SentryId object) of the event
   */
  default SentryId captureEnvelope(SentryEnvelope envelope) {
    return captureEnvelope(envelope, null);
  }

  SentryId captureTransaction(Transaction transaction, Scope scope, Object hint);

  default SentryId captureTransaction(Transaction transaction) {
    return captureTransaction(transaction, null, null);
  }
}
