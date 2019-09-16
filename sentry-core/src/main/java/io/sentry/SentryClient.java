package io.sentry;

import java.util.UUID;

public class SentryClient implements ISentryClient {
  private boolean isEnabled;

  private SentryOptions options;

  public boolean isEnabled() {
    return isEnabled;
  }

  public SentryClient(SentryOptions options) {
    this.options = options;
    this.isEnabled = true;
  }

  public UUID captureEvent(SentryEvent event) {
    return UUID.randomUUID();
  }

  public void close() {
    // TODO: Flush events
    isEnabled = false;
  }
}
