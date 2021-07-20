package io.sentry;

import io.sentry.hints.DiskFlushNotification;
import io.sentry.util.ApplyScopeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class ClientSessionUpdater implements SessionUpdater {
  private final SentryOptions options;

  public ClientSessionUpdater(SentryOptions options) {
    this.options = options;
  }

  @TestOnly
  @Nullable
  @Override
  public Session updateSessionData(
      final @NotNull SentryEvent event, final @Nullable Object hint, final @Nullable Scope scope) {
    Session clonedSession = null;

    if (ApplyScopeUtils.shouldApplyScopeData(hint)) {
      if (scope != null) {
        clonedSession =
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
                      // if hint is DiskFlushNotification, it means we have an uncaughtException
                      // and we can end the session.
                      if (hint instanceof DiskFlushNotification) {
                        session.end();
                      }
                    }
                  } else {
                    options
                        .getLogger()
                        .log(SentryLevel.INFO, "Session is null on scope.withSession");
                  }
                });
      } else {
        options.getLogger().log(SentryLevel.INFO, "Scope is null on client.captureEvent");
      }
    }
    return clonedSession;
  }
}
