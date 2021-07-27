package io.sentry;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Collects session statistics. The instance of SessionAggregates is shared between all instances of
 * {@link Hub}.
 */
@ApiStatus.Internal
public final class SessionAggregates {
  private final @NotNull Map<String, SessionStats> aggregates = new ConcurrentHashMap<>();
  private final @NotNull Attributes attributes;

  public SessionAggregates(final @NotNull String release, final @Nullable String environment) {
    this.attributes = new Attributes(release, environment);
  }

  @SuppressWarnings("JavaUtilDate")
  void addSession(final @NotNull ServerSessionManager.Status state) {
    addSession(new Date(), state);
  }

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
  public Map<String, SessionStats> getAggregates() {
    return aggregates;
  }

  @NotNull
  public Attributes getAttributes() {
    return attributes;
  }

  public static final class Attributes {
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
