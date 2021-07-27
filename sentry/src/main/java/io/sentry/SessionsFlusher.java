package io.sentry;

import io.sentry.protocol.Sessions;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import org.jetbrains.annotations.NotNull;

/** Periodically flushes aggregated server side {@link Sessions} to Sentry. */
final class SessionsFlusher implements Closeable {
  private static final int ONE_MINUTE = 60 * 1000;

  private final @NotNull SessionAggregates aggregates;
  private final @NotNull ISentryClient sentryClient;
  private final @NotNull Timer timer = new Timer();

  public SessionsFlusher(
      final @NotNull SessionAggregates sessionAggregates,
      final @NotNull ISentryClient sentryClient) {
    this.aggregates = Objects.requireNonNull(sessionAggregates, "sessionAggregates is required");
    this.sentryClient = Objects.requireNonNull(sentryClient, "sentryClient is required");
  }

  void start() {
    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            if (!aggregates.getAggregates().isEmpty()) {
              sentryClient.captureSessions(new Sessions(aggregates));
              aggregates.getAggregates().clear();
            }
          }
        },
        ONE_MINUTE,
        ONE_MINUTE);
  }

  @Override
  public void close() throws IOException {
    timer.cancel();
  }
}
