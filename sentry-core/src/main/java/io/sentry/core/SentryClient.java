package io.sentry.core;

import static io.sentry.core.ILogger.logIfNotNull;

import io.sentry.core.cache.DiskCache;
import io.sentry.core.cache.IEventCache;
import io.sentry.core.hints.Cached;
import io.sentry.core.protocol.SentryId;
import io.sentry.core.transport.Connection;
import io.sentry.core.transport.ITransport;
import io.sentry.core.transport.ITransportGate;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

public final class SentryClient implements ISentryClient {
  static final String SENTRY_PROTOCOL_VERSION = "7";

  private boolean enabled;

  private final SentryOptions options;
  private final Connection connection;
  private final Random random;

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  SentryClient(SentryOptions options) {
    this(options, null);
  }

  public SentryClient(SentryOptions options, @Nullable Connection connection) {
    this.options = options;
    this.enabled = true;

    ITransport transport = options.getTransport();
    if (transport == null) {
      transport = HttpTransportFactory.create(options);
      options.setTransport(transport);
    }

    ITransportGate transportGate = options.getTransportGate();
    if (transportGate == null) {
      transportGate = () -> true;
      options.setTransportGate(transportGate);
    }

    if (connection == null) {
      // TODO this is obviously provisional and should be constructed based on the config in options
      IEventCache cache = new DiskCache(options);

      connection = AsyncConnectionFactory.create(options, cache);
    }
    this.connection = connection;
    random = options.getSampleRate() == null ? null : new Random();
  }

  @Override
  public SentryId captureEvent(SentryEvent event, @Nullable Scope scope, @Nullable Object hint) {
    if (!sample()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.DEBUG,
          "Event %s was dropped due to sampling decision.",
          event.getEventId());
      return SentryId.EMPTY_ID;
    }

    logIfNotNull(options.getLogger(), SentryLevel.DEBUG, "Capturing event: %s", event.getEventId());

    if (!(hint instanceof Cached)) {
      // Event has already passed through here before it was cached
      // Going through again could be reading data that is no longer relevant
      // i.e proguard id, app version, threads
      event = applyScope(event, scope, hint);

      if (event == null) {
        // event dropped by the scope event processors
        return SentryId.EMPTY_ID;
      }
    } else {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.DEBUG,
          "Event was cached so not applying scope: %s",
          event.getEventId());
    }

    for (EventProcessor processor : options.getEventProcessors()) {
      event = processor.process(event, hint);
    }

    event = executeBeforeSend(event, hint);

    if (event == null) {
      // Event dropped by the beforeSend callback
      return SentryId.EMPTY_ID;
    }

    try {
      connection.send(event, hint);
    } catch (IOException e) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Capturing event " + event.getEventId() + " failed.",
          e);
    }

    return event.getEventId();
  }

  private SentryEvent applyScope(SentryEvent event, @Nullable Scope scope, @Nullable Object hint) {
    if (scope != null) {
      if (event.getTransaction() == null) {
        event.setTransaction(scope.getTransaction());
      }
      if (event.getUser() == null) {
        event.setUser(scope.getUser());
      }
      if (event.getFingerprints() == null) {
        event.setFingerprints(scope.getFingerprint());
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
      if (event.getExtras() == null) {
        event.setExtras(new HashMap<>(scope.getExtras()));
      } else {
        for (Map.Entry<String, Object> item : scope.getExtras().entrySet()) {
          if (!event.getExtras().containsKey(item.getKey())) {
            event.getExtras().put(item.getKey(), item.getValue());
          }
        }
      }
      // Level from scope exceptionally take precedence over the event
      if (scope.getLevel() != null) {
        event.setLevel(scope.getLevel());
      }

      for (EventProcessor processor : scope.getEventProcessors()) {
        event = processor.process(event, hint);

        if (event == null) {
          break;
        }
      }
    }
    return event;
  }

  private SentryEvent executeBeforeSend(SentryEvent event, @Nullable Object hint) {
    SentryOptions.BeforeSendCallback beforeSend = options.getBeforeSend();
    if (beforeSend != null) {
      try {
        event = beforeSend.execute(event, hint);
      } catch (Exception e) {
        logIfNotNull(
            options.getLogger(),
            SentryLevel.ERROR,
            "The BeforeSend callback threw an exception. It will be added as breadcrumb and continue.",
            e);

        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setMessage("BeforeSend callback failed.");
        breadcrumb.setCategory("SentryClient");
        Map<String, String> data = new HashMap<>();
        data.put("sentry:message", e.getMessage());
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        data.put("sentry:stacktrace", sw.toString()); // might be obfuscated
        breadcrumb.setLevel(SentryLevel.ERROR);
        breadcrumb.setData(data);
        event.addBreadcrumb(breadcrumb);
      }
    }
    return event;
  }

  @Override
  public void close() {
    logIfNotNull(options.getLogger(), SentryLevel.INFO, "Closing SDK.");

    try {
      flush(options.getShutdownTimeout());
      connection.close();
    } catch (IOException e) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Failed to close the connection to the Sentry Server.",
          e);
    }
    enabled = false;
  }

  @Override
  public void flush(long timeoutMills) {
    // TODO: Flush transport
  }

  private boolean sample() {
    // https://docs.sentry.io/development/sdk-dev/features/#event-sampling
    if (options.getSampleRate() != null && random != null) {
      double sampling = options.getSampleRate();
      return !(sampling < random.nextDouble()); // bad luck
    }
    return true;
  }
}
