package io.sentry;

import java.io.Closeable;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Collects session statistics and flushes them to Sentry every 1 minute. Instance of
 * SessionAggregates is shared between all instances of {@link Hub}.
 */
final class SessionAggregates implements Closeable {
  private final @NotNull Map<String, SessionStats> aggregates = new ConcurrentHashMap<>();
  private final @NotNull Attributes attributes;

  private static final int ONE_MINUTE = 60 * 1000;
  private final @NotNull Timer timer = new Timer();

  public SessionAggregates(final @NotNull String release, final @Nullable String environment) {
    this.attributes = new Attributes(release, environment);
  }

  void start() {
    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            // TODO: serialize and send envelopes
            for (Map.Entry<String, SessionAggregates.SessionStats> entry : aggregates.entrySet()) {
              System.out.println(entry.getKey() + ":" + entry.getValue());
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

  // todo: infer startedAt
  void addSession(final @NotNull Date startedAt, final @NotNull ServerSessionManager.Status state) {
    final String roundedDate = DateUtils.getTimestampMinutesPrecision(startedAt);
    SessionStats stats;
    if (this.aggregates.containsKey(roundedDate)) {
      stats = this.aggregates.get(roundedDate);
    } else {
      stats = new SessionStats();
      this.aggregates.put(roundedDate, stats);
    }

    switch (state) {
      case Exited:
        stats.exited.incrementAndGet();
        break;
      case Errored:
        stats.errored.incrementAndGet();
        break;
      case Crashed:
        stats.crashed.incrementAndGet();
        break;
      default:
        break;
    }
  }

  @NotNull
  Map<String, SessionStats> getAggregates() {
    return aggregates;
  }

  @NotNull
  Attributes getAttributes() {
    return attributes;
  }

  static final class Attributes {
    private final @NotNull String release;
    private final @Nullable String environment;

    public Attributes(final @NotNull String release, final @Nullable String environment) {
      this.release = release;
      this.environment = environment;
    }

    public @NotNull String getRelease() {
      return release;
    }

    public @Nullable String getEnvironment() {
      return environment;
    }
  }

  static final class SessionStats {
    private final @NotNull AtomicLong exited = new AtomicLong();
    private final @NotNull AtomicLong errored = new AtomicLong();
    private final @NotNull AtomicLong crashed = new AtomicLong();

    public @NotNull AtomicLong getExited() {
      return exited;
    }

    public @NotNull AtomicLong getErrored() {
      return errored;
    }

    public @NotNull AtomicLong getCrashed() {
      return crashed;
    }

    @Override
    public String toString() {
      return "SessionStats{"
          + "exited="
          + exited
          + ", errored="
          + errored
          + ", crashed="
          + crashed
          + '}';
    }
  }
}
