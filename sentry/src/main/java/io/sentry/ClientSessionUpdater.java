package io.sentry;

import io.sentry.hints.DiskFlushNotification;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ClientSessionUpdater implements SessionUpdater {
  private final @NotNull SentryOptions options;

  public ClientSessionUpdater(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options is required");
  }

  @Nullable
  @Override
  public Session updateSessionData(
      final @NotNull SentryEvent event,
      final @Nullable Map<String, Object> hint,
      final @Nullable Scope scope) {
    Session clonedSession = null;

    if (HintUtils.shouldApplyScopeData(hint)) {
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

                    if (session.update(status, crashedOrErrored)) {
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
        options
            .getLogger()
            .log(SentryLevel.INFO, "Scope is null on sessionUpdater.updateSessionData");
      }
    }
    return clonedSession;
  }
}
