package io.sentry;

import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ServerSessionManager implements SessionTracker, SessionUpdater {
  private static final int ONE_MINUTE = 60 * 1000;
  private final @NotNull Timer timer = new Timer();
  private final @NotNull SessionAggregates sessionAggregates;

  ServerSessionManager(final @NotNull String release, final @Nullable String environment) {
    sessionAggregates =
        new SessionAggregates(new SessionAggregates.Attributes(release, environment));
  }

  void start() {
    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            // TODO: serialize and send envelopes
            for (Map.Entry<String, SessionAggregates.SessionStats> entry :
                sessionAggregates.getAggregates().entrySet()) {
              System.out.println(entry.getKey() + ":" + entry.getValue());
            }
          }
        },
        ONE_MINUTE,
        ONE_MINUTE);
  }

  void stop() {
    timer.cancel();
  }

  @Override
  public void startSession() {
    // do nothing
  }

  @Override
  @SuppressWarnings("JavaUtilDate")
  public void endSession() {
    sessionAggregates.addSession(new Date(), Session.State.Exited);
  }

  @Override
  @SuppressWarnings("JavaUtilDate")
  public @Nullable Session updateSessionData(
      @NotNull SentryEvent event, @Nullable Object hint, @Nullable Scope scope) {
    // todo: check if event.isCrashed() ?
    sessionAggregates.addSession(new Date(), Session.State.Crashed);
    return null;
  }
}
