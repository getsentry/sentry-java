package io.sentry;

import io.sentry.protocol.SentryId;

public interface ISentryClient {
  boolean isEnabled();

  SentryId captureEvent(SentryEvent event);

  SentryId captureMessage(String message);

  SentryId captureException(Throwable throwable);

  void close();
}
