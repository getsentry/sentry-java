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
      @NotNull SentryEvent event, @NotNull Hint hint, @Nullable Scope scope) {
    // If the status is still just 'null' or 'Exited'
    // perhaps promote it to errored or crashed:
    if (status != Status.Crashed) {
      if (event.isCrashed()) {
        status = Status.Crashed;
      } else if (event.isErrored()) {
        status = Status.Errored;
      }
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
