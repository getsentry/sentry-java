package io.sentry.core;

import io.sentry.core.hints.DiskFlushNotification;
import io.sentry.core.hints.SessionEndHint;
import io.sentry.core.hints.SessionUpdateHint;
import io.sentry.core.protocol.SentryId;
import io.sentry.core.transport.Connection;
import io.sentry.core.transport.ITransport;
import io.sentry.core.transport.NoOpTransport;
import io.sentry.core.util.ApplyScopeUtils;
import io.sentry.core.util.Objects;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SentryClient implements ISentryClient {
  static final String SENTRY_PROTOCOL_VERSION = "7";

  private boolean enabled;

  private final @NotNull SentryOptions options;
  private final @NotNull Connection connection;
  private final @Nullable Random random;

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  SentryClient(final @NotNull SentryOptions options) {
    this(options, null);
  }

  public SentryClient(final @NotNull SentryOptions options, @Nullable Connection connection) {
    this.options = Objects.requireNonNull(options, "SentryOptions is required.");
    this.enabled = true;

    ITransport transport = options.getTransport();
    if (transport instanceof NoOpTransport) {
      transport = HttpTransportFactory.create(options);
      options.setTransport(transport);
    }

    if (connection == null) {
      connection =
          AsyncConnectionFactory.create(
              options, options.getEventDiskCache(), options.getEnvelopeDiskCache());
    }
    this.connection = connection;
    random = options.getSampleRate() == null ? null : new Random();
  }

  @Override
  public @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, final @Nullable Scope scope, final @Nullable Object hint) {
    Objects.requireNonNull(event, "SentryEvent is required.");

    options.getLogger().log(SentryLevel.DEBUG, "Capturing event: %s", event.getEventId());

    if (ApplyScopeUtils.shouldApplyScopeData(hint)) {
      // Event has already passed through here before it was cached
      // Going through again could be reading data that is no longer relevant
      // i.e proguard id, app version, threads
      event = applyScope(event, scope, hint);

      if (event == null) {
        return SentryId.EMPTY_ID;
      }
    } else {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Event was cached so not applying scope: %s", event.getEventId());
    }

    for (EventProcessor processor : options.getEventProcessors()) {
      event = processor.process(event, hint);

      if (event == null) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Event was dropped by processor: %s",
                processor.getClass().getName());
        break;
      }
    }

    if (event == null) {
      return SentryId.EMPTY_ID;
    }

    // TODO: should it be done on the HUB?
    updateSessionData(event, hint, scope);

    if (!sample()) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Event %s was dropped due to sampling decision.",
              event.getEventId());
      return SentryId.EMPTY_ID;
    }

    event = executeBeforeSend(event, hint);

    if (event == null) {
      options.getLogger().log(SentryLevel.DEBUG, "Event was dropped by beforeSend");
      return SentryId.EMPTY_ID;
    }

    try {
      connection.send(event, hint);
    } catch (IOException e) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Capturing event " + event.getEventId() + " failed.", e);
    }

    return event.getEventId();
  }

  /**
   * Updates the session data based on the event, hint and scope data
   *
   * @param event the SentryEvent
   * @param hint the hint or null
   * @param scope the Scope or null
   */
  @TestOnly
  void updateSessionData(
      final @NotNull SentryEvent event, final @Nullable Object hint, final @Nullable Scope scope) {
    if (ApplyScopeUtils.shouldApplyScopeData(hint)) {
      if (scope != null) {
        scope.withSession(
            session -> {
              if (session != null) {
                Session.State status = null;
                if (event.isCrashed()) {
                  status = Session.State.Crashed;
                }

                boolean crashedOrErrored = false;
                if (Session.State.Crashed == status || event.isErrored()) {
                  crashedOrErrored = true;
                }

                String userAgent = null;
                if (event.getRequest() != null && event.getRequest().getHeaders() != null) {
                  if (event.getRequest().getHeaders().containsKey("user-agent")) {
                    userAgent = event.getRequest().getHeaders().get("user-agent");
                  }
                }

                if (session.update(status, userAgent, crashedOrErrored)) {

                  Object sessionHint;

                  // if hint is DiskFlushNotification, it means we have an uncaughtException
                  // and we can end the session.
                  if (hint instanceof DiskFlushNotification) {
                    sessionHint = new SessionEndHint();
                    session.end();
                  } else {
                    // otherwise we just cache in the disk but do not flush to the network.
                    sessionHint = new SessionUpdateHint();
                  }
                  captureSession(session, sessionHint);
                }
              } else {
                options.getLogger().log(SentryLevel.INFO, "Session is null on scope.withSession");
              }
            });
      } else {
        options.getLogger().log(SentryLevel.INFO, "Scope is null on client.captureEvent");
      }
    }
  }

  @ApiStatus.Internal
  @Override
  public void captureSession(final @NotNull Session session, final @Nullable Object hint) {
    Objects.requireNonNull(session, "Session is required.");

    if (session.getRelease() == null || session.getRelease().isEmpty()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Sessions can't be captured without setting a release.");
      return;
    }

    SentryEnvelope envelope;
    try {
      envelope =
          SentryEnvelope.fromSession(options.getSerializer(), session, options.getSdkVersion());
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to capture session.", e);
      return;
    }

    captureEnvelope(envelope, hint);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable SentryId captureEnvelope(
      final @NotNull SentryEnvelope envelope, final @Nullable Object hint) {
    Objects.requireNonNull(envelope, "SentryEnvelope is required.");

    try {
      connection.send(envelope, hint);
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to capture envelope.", e);
      return SentryId.EMPTY_ID;
    }
    return envelope.getHeader().getEventId();
  }

  private @Nullable SentryEvent applyScope(
      @NotNull SentryEvent event, final @Nullable Scope scope, final @Nullable Object hint) {
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
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Event was dropped by scope processor: %s",
                  processor.getClass().getName());
          break;
        }
      }
    }
    return event;
  }

  private @Nullable SentryEvent executeBeforeSend(
      @NotNull SentryEvent event, final @Nullable Object hint) {
    final SentryOptions.BeforeSendCallback beforeSend = options.getBeforeSend();
    if (beforeSend != null) {
      try {
        event = beforeSend.execute(event, hint);
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "The BeforeSend callback threw an exception. It will be added as breadcrumb and continue.",
                e);

        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setMessage("BeforeSend callback failed.");
        breadcrumb.setCategory("SentryClient");
        breadcrumb.setLevel(SentryLevel.ERROR);
        breadcrumb.setData("sentry:message", e.getMessage());
        event.addBreadcrumb(breadcrumb);
      }
    }
    return event;
  }

  @Override
  public void close() {
    options.getLogger().log(SentryLevel.INFO, "Closing SentryClient.");

    try {
      flush(options.getShutdownTimeout());
      connection.close();
    } catch (IOException e) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Failed to close the connection to the Sentry Server.", e);
    }
    enabled = false;
  }

  @Override
  public void flush(long timeoutMillis) {
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
