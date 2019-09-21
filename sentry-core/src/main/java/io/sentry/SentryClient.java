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
    ILogger logger = options.getLogger();
    if (logger != null) {
      logger.log(SentryLevel.Debug, "Capturing event: %d", event.getEventId());
    }
    return event.getEventId();
  }

  public void close() {
    ILogger logger = options.getLogger();
    if (logger != null) {
      logger.log(SentryLevel.Info, "Closing SDK.");
    }
    // TODO: Flush events
    isEnabled = false;
  }
}
