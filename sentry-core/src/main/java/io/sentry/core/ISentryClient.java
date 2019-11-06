package io.sentry.core;

import io.sentry.core.protocol.Message;
import io.sentry.core.protocol.SentryId;
import org.jetbrains.annotations.Nullable;

public interface ISentryClient {
  boolean isEnabled();

  SentryId captureEvent(SentryEvent event, @Nullable Scope scope, @Nullable Object hint);

  void close();

  void flush(long timeoutMills);

  default SentryId captureEvent(SentryEvent event) {
    return captureEvent(event, null, null);
  }

  default SentryId captureEvent(SentryEvent event, @Nullable Scope scope) {
    return captureEvent(event, scope, null);
  }

  default SentryId captureEvent(SentryEvent event, @Nullable Object hint) {
    return captureEvent(event, null, hint);
  }

  default SentryId captureMessage(String message) {
    return captureMessage(message, null);
  }

  default SentryId captureMessage(String message, @Nullable Scope scope) {
    SentryEvent event = new SentryEvent();
    Message sentryMessage = new Message();
    sentryMessage.setFormatted(message);
    event.setMessage(sentryMessage);
    return captureEvent(event, scope);
  }

  default SentryId captureException(Throwable throwable) {
    return captureException(throwable, null, null);
  }

  default SentryId captureException(
      Throwable throwable, @Nullable Scope scope, @Nullable Object hint) {
    SentryEvent event = new SentryEvent(throwable);
    return captureEvent(event, scope, hint);
  }

  default SentryId captureException(Throwable throwable, @Nullable Object hint) {
    return captureException(throwable, null, hint);
  }

  default SentryId captureException(Throwable throwable, @Nullable Scope scope) {
    return captureException(throwable, scope, null);
  }
}
