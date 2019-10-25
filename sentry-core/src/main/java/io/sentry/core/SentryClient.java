package io.sentry.core;

import static io.sentry.core.ILogger.log;

import io.sentry.core.protocol.SentryId;
import io.sentry.core.transport.AsyncConnection;
import io.sentry.core.util.Nullable;
import java.io.IOException;

public class SentryClient implements ISentryClient {
  static final String SENTRY_PROTOCOL_VERSION = "7";

  private boolean isEnabled;

  private final SentryOptions options;
  private final AsyncConnection connection;

  public boolean isEnabled() {
    return isEnabled;
  }

  public SentryClient(SentryOptions options) {
    this(options, null);
  }

  public SentryClient(SentryOptions options, @Nullable AsyncConnection connection) {
    this.options = options;
    this.isEnabled = true;
    if (connection == null) {
      connection = AsyncConnectionFactory.create(options);
    }
    this.connection = connection;
  }

  public SentryId captureEvent(SentryEvent event, @Nullable Scope scope) {
    log(options.getLogger(), SentryLevel.DEBUG, "Capturing event: %s", event.getEventId());

    // TODO: To be done in the main processor
    if (event.getRelease() == null) {
      event.setRelease(options.getRelease());
    }
    if (event.getEnvironment() == null) {
      event.setEnvironment(options.getEnvironment());
    }

    for (EventProcessor processor : options.getEventProcessors()) {
      processor.process(event);
    }

    SentryOptions.BeforeSendCallback beforeSend = options.getBeforeSend();
    if (beforeSend != null) {
      event = beforeSend.execute(event);
      if (event == null) {
        // Event dropped by the beforeSend callback
        return SentryId.EMPTY_ID;
      }
    }

    try {
      connection.send(event);
    } catch (IOException e) {
      log(
          options.getLogger(),
          SentryLevel.WARNING,
          "Capturing event " + event.getEventId() + " failed.",
          e);
    }

    return event.getEventId();
  }

  @Override
  public SentryId captureEvent(SentryEvent event) {
    return captureEvent(event, null);
  }

  public void close() {
    log(options.getLogger(), SentryLevel.INFO, "Closing SDK.");

    try {
      flush(options.getShutdownTimeout());
      connection.close();
    } catch (IOException e) {
      log(
          options.getLogger(),
          SentryLevel.WARNING,
          "Failed to close the connection to the Sentry Server.",
          e);
    }
    isEnabled = false;
  }

  @Override
  public void flush(long timeoutMills) {
    // TODO: Flush transport
  }
}
