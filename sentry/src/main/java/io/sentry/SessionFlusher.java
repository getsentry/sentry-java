package io.sentry;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SessionFlusher {
  private static final int ONE_MINUTE = 60 * 1000;
  private final @NotNull Timer timer = new Timer();
  private final @NotNull SessionAggregates sessionAggregates;

  SessionFlusher(final @NotNull String release, final @Nullable String environment) {
    sessionAggregates =
        new SessionAggregates(new SessionAggregates.Attributes(release, environment));
  }

  void addSession(final @NotNull Session session) {
    sessionAggregates.addSession(session);
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
}
