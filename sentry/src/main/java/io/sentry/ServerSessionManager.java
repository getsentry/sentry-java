package io.sentry;

import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ServerSessionManager implements SessionTracker, SessionUpdater {
  private @Nullable Status status;
  private final @NotNull SessionAggregates sessionAggregates;

  ServerSessionManager(final @NotNull SessionAggregates sessionAggregates) {
    this.sessionAggregates =
        Objects.requireNonNull(sessionAggregates, "sessionAggregates is required");
  }

  @Override
  public void startSession() {
    status = null;
  }

  @Override
  public void endSession() {
    if (status == null) {
      status = Status.Exited;
    }
    sessionAggregates.addSession(status);
  }

  @Override
  public @Nullable Session updateSessionData(
      @NotNull SentryEvent event, @Nullable Object hint, @Nullable Scope scope) {
    if (status != Status.Crashed) {
      status = event.isCrashed() ? Status.Crashed : Status.Errored;
    }
    return null;
  }

  @ApiStatus.Internal
  public enum Status {
    Exited,
    Errored,
    Crashed;
  }
}
