package io.sentry.core;

import io.sentry.core.protocol.Message;
import io.sentry.core.protocol.SentryId;
import io.sentry.core.util.Nullable;

public interface ISentryClient {
  boolean isEnabled();

  SentryId captureEvent(SentryEvent event, @Nullable Scope scope);

  void close();

  void flush(long timeoutMills);

  default SentryId captureEvent(SentryEvent event) {
    return captureEvent(event, null);
  }

  default SentryId captureMessage(String message) {
    return captureMessage(message, null);
  }

  default SentryId captureMessage(String message, @Nullable Scope scope) {
    SentryEvent event = new SentryEvent();
    Message sentryMessage = new Message();
    sentryMessage.setFormatted(message);
    return captureEvent(event, scope);
  }

  default SentryId captureException(Throwable throwable) {
    return captureException(throwable, null);
  }

  default SentryId captureException(Throwable throwable, @Nullable Scope scope) {
    SentryEvent event = new SentryEvent(throwable);
    return captureEvent(event, scope);
  }
}
