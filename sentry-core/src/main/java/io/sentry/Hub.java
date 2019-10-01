package io.sentry;

import io.sentry.protocol.SentryId;

public class Hub implements IHub {
  public Hub(SentryOptions options) {}

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public SentryId captureEvent(SentryEvent event) {
    return null;
  }

  @Override
  public SentryId captureMessage(String message) {
    return null;
  }

  @Override
  public SentryId captureException(Throwable throwable) {
    return null;
  }

  @Override
  public void close() {}
}
