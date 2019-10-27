package io.sentry.core;

import static io.sentry.core.ILogger.log;

import io.sentry.core.protocol.SentryId;
import io.sentry.core.transport.AsyncConnection;
import io.sentry.core.util.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

    if (scope != null) {
      if (event.getTransaction() == null) {
        event.setTransaction(scope.getTransaction());
      }
      if (event.getUser() == null) {
        event.setUser(scope.getUser());
      }
      if (event.getFingerprint() == null) {
        event.setFingerprint(scope.getFingerprint());
      }
      if (event.getBreadcrumbs() == null) {
        event.setBreadcrumbs(new ArrayList<>(scope.getBreadcrumbs()));
      } else {
        event.getBreadcrumbs().addAll(scope.getBreadcrumbs());
      }
      if (event.getTags() == null) {
        event.setTags(new HashMap<>(scope.getTags()));
      } else {
        for (Map.Entry<String, String> item : scope.getTags().entrySet()) {
          if (!event.getTags().containsKey(item.getKey())) {
            event.getTags().put(item.getKey(), item.getValue());
          }
        }
      }
      if (event.getExtra() == null) {
        event.setExtra(new HashMap<>(scope.getExtra()));
      } else {
        for (Map.Entry<String, Object> item : scope.getExtra().entrySet()) {
          if (!event.getExtra().containsKey(item.getKey())) {
            event.getExtra().put(item.getKey(), item.getValue());
          }
        }
      }
      // Level from scope exceptionally take precedence over the event
      if (scope.getLevel() != null) {
        event.setLevel(scope.getLevel());
      }
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
