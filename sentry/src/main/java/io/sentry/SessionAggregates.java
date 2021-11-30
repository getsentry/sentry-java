package io.sentry;

import io.sentry.util.Objects;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Collects session statistics. The instance of SessionAggregates is shared between all instances of
 * {@link Hub}.
 */
@ApiStatus.Internal
public final class SessionAggregates {
  private final @NotNull AtomicReference<Map<String, SessionStats>> aggregates =
      new AtomicReference<>(new ConcurrentHashMap<>());
  private final @NotNull Attributes attributes;

  public SessionAggregates(final @NotNull String release, final @Nullable String environment) {
    this.attributes = new Attributes(release, environment);
  }

  @SuppressWarnings("JavaUtilDate")
  void addSession(final @NotNull ServerSessionManager.Status state) {
    addSession(new Date(), state);
  }

  public void addSession(
      final @NotNull Date startedAt, final @NotNull ServerSessionManager.Status state) {
    final String roundedDate = DateUtils.getTimestampMinutesPrecision(startedAt);
    SessionStats stats;
    final Map<String, SessionStats> statsMap = this.aggregates.get();
    if (statsMap != null) {
      stats = statsMap.get(roundedDate);
      if (stats == null) {
        synchronized (this) {
          stats = statsMap.get(roundedDate);
          if (stats == null) {
            stats = new SessionStats();
            statsMap.put(roundedDate, stats);
          }
        }
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
  }

  /**
   * Sets aggregates to a new empty map and returns the previous value.
   *
   * @return aggregates value before resetting
   */
  @NotNull
  public Map<String, SessionStats> resetAggregates() {
    return aggregates.getAndSet(new ConcurrentHashMap<>());
  }

  @NotNull
  public Attributes getAttributes() {
    return attributes;
  }

  public static final class Attributes {
    private final @NotNull String release;
    private final @Nullable String environment;

    public Attributes(final @NotNull String release, final @Nullable String environment) {
      this.release = Objects.requireNonNull(release, "release is required");
      this.environment = environment;
    }

    public @NotNull String getRelease() {
      return release;
    }

    public @Nullable String getEnvironment() {
      return environment;
    }
  }

  public static final class SessionStats {
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
  }
}
