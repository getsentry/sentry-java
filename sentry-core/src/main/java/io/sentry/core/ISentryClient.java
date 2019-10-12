package io.sentry.core;

import io.sentry.core.protocol.SentryId;

public interface ISentryClient {
  boolean isEnabled();

  SentryId captureEvent(SentryEvent event);

  SentryId captureMessage(String message);

  SentryId captureException(Throwable throwable);

  void close();
}
