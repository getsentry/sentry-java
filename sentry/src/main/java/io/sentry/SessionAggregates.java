package io.sentry;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SessionAggregates {
  private final @NotNull Map<String, SessionStats> aggregates = new ConcurrentHashMap<>();
  private final @NotNull Attributes attributes;

  public SessionAggregates(@NotNull Attributes attributes) {
    this.attributes = attributes;
  }

  void addSession(final @NotNull Date startedAt, final @NotNull Session.State state) {
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
      case Crashed:
        stats.errored.incrementAndGet();
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

    public Attributes(@NotNull String release, @Nullable String environment) {
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

    public @NotNull AtomicLong getExited() {
      return exited;
    }

    public @NotNull AtomicLong getErrored() {
      return errored;
    }

    @Override
    public String toString() {
      return "SessionStats{" + "exited=" + exited + ", errored=" + errored + '}';
    }
  }
}
