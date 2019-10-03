package io.sentry;

import io.sentry.protocol.Message;
import io.sentry.protocol.SentryId;

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

  public SentryId captureEvent(SentryEvent event) {
    ILogger logger = options.getLogger();
    if (logger != null) {
      logger.log(SentryLevel.Debug, "Capturing event: %s", event.getEventId());
    }
    return event.getEventId();
  }

  @Override
  public SentryId captureMessage(String message) {
    SentryEvent event = new SentryEvent();
    Message sentryMessage = new Message();
    sentryMessage.setFormatted(message);
    return captureEvent(event);
  }

  @Override
  public SentryId captureException(Throwable throwable) {
    SentryEvent event = new SentryEvent(throwable);
    return captureEvent(event);
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
