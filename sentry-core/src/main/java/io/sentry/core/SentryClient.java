package io.sentry.core;

import static io.sentry.core.ILogger.log;

import io.sentry.core.protocol.Message;
import io.sentry.core.protocol.SentryId;
import io.sentry.core.transport.AsyncConnection;
import io.sentry.core.transport.HttpTransport;
import io.sentry.core.transport.IBackOffIntervalStrategy;
import io.sentry.core.transport.IConnectionConfigurator;
import io.sentry.core.transport.IEventCache;
import io.sentry.core.transport.ITransportGate;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

public class SentryClient implements ISentryClient {
  static final String SENTRY_PROTOCOL_VERSION = "7";

  private boolean isEnabled;

  private final SentryOptions options;
  private final AsyncConnection connection;

  public boolean isEnabled() {
    return isEnabled;
  }

  public SentryClient(SentryOptions options) {
    this.options = options;
    this.isEnabled = true;
    this.connection = buildConnection(options);
  }

  private static AsyncConnection buildConnection(SentryOptions options) {
    try {
      IConnectionConfigurator setCredentials = new CredentialsSettingConfigurator(options);

      HttpTransport transport = new HttpTransport(options, null, setCredentials, 60, 60, true);

      // TODO this should be made configurable at least for the Android case where we can
      // just not attempt to send if the device is offline.
      ITransportGate alwaysOn =
          new ITransportGate() {
            @Override
            public boolean isSendingAllowed() {
              return true;
            }
          };

      IBackOffIntervalStrategy linearBackoff =
          new IBackOffIntervalStrategy() {
            @Override
            public long nextDelayMillis(int attempt) {
              return attempt * 500;
            }
          };

      // TODO this is obviously provisional and should be constructed based on the config in options
      IEventCache blackHole =
          new IEventCache() {
            @Override
            public void store(SentryEvent event) {}

            @Override
            public void discard(SentryEvent event) {}
          };

      // the connection doesn't do any retries of failed sends and can hold at most 10
      // pending events. The rest is dropped.
      return new AsyncConnection(transport, alwaysOn, linearBackoff, blackHole, 0, 10, options);
    } catch (URISyntaxException | MalformedURLException e) {
      throw new IllegalArgumentException(
          "Failed to compose the connection to the Sentry server.", e);
    }
  }

  public SentryId captureEvent(SentryEvent event) {
    log(options.getLogger(), SentryLevel.DEBUG, "Capturing event: %s", event.getEventId());

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
    log(options.getLogger(), SentryLevel.INFO, "Closing SDK.");

    try {
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
}
