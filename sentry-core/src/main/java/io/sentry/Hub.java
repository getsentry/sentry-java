package io.sentry;

import io.sentry.protocol.SentryId;

public class Hub implements IHub {
  private SentryOptions options;
  private volatile boolean isEnabled;

  public Hub(SentryOptions options) {
    this.options = options;
    isEnabled = true;
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
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
  public void close() {
    isEnabled = false;
  }

  @Override
  public IHub clone() {
    return new Hub(options);
  }
}
