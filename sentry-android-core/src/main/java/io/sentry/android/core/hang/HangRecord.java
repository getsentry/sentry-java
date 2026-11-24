package io.sentry.android.core.hang;

import io.sentry.android.core.anr.AnrStackTrace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** The collected data of one recorded hang. */
@ApiStatus.Internal
public final class HangRecord {

  private final long durationMs;
  private final long startTimestampMs;
  private final @NotNull List<AnrStackTrace> stackTraces;
  private final @NotNull List<RuntimeStatsSnapshot> runtimeStats;
  private final boolean endedAtMaxRecordingTime;

  private HangRecord(final @NotNull Builder builder) {
    this.durationMs = builder.durationMs;
    this.startTimestampMs = builder.startTimestampMs;
    this.stackTraces = Collections.unmodifiableList(new ArrayList<>(builder.stackTraces));
    this.runtimeStats = Collections.unmodifiableList(new ArrayList<>(builder.runtimeStats));
    this.endedAtMaxRecordingTime = builder.endedAtMaxRecordingTime;
  }

  public long getDurationMs() {
    return durationMs;
  }

  /** The start of the hang, as Unix time in milliseconds. */
  public long getStartTimestampMs() {
    return startTimestampMs;
  }

  /** The stacks of the watched thread, in the order they were sampled. */
  public @NotNull List<AnrStackTrace> getStackTraces() {
    return stackTraces;
  }

  /** The runtime counters, in the order they were read. Empty if no collector read them. */
  public @NotNull List<RuntimeStatsSnapshot> getRuntimeStats() {
    return runtimeStats;
  }

  /**
   * Whether the hang was cut at the maximum recording time. The watched thread is then still
   * blocked when the record is given to the listeners.
   */
  public boolean hasEndedAtMaxRecordingTime() {
    return endedAtMaxRecordingTime;
  }

  /** Builds a {@link HangRecord}. */
  public static final class Builder {
    private long durationMs;
    private long startTimeMs;
    private long startTimestampMs;
    private final @NotNull List<AnrStackTrace> stackTraces = new ArrayList<>();
    private final @NotNull List<RuntimeStatsSnapshot> runtimeStats = new ArrayList<>();
    private boolean endedAtMaxRecordingTime;
    private boolean shouldPersist;
    private int sampleCount;

    public @NotNull Builder setDurationMs(final long durationMs) {
      this.durationMs = durationMs;
      return this;
    }

    /** The start of the hang, as a monotonic time in milliseconds. */
    public @NotNull Builder setStartTimeMs(final long startTimeMs) {
      this.startTimeMs = startTimeMs;
      return this;
    }

    public long getStartTimeMs() {
      return startTimeMs;
    }

    /** The start of the hang, as Unix time in milliseconds. */
    public @NotNull Builder setStartTimestampMs(final long startTimestampMs) {
      this.startTimestampMs = startTimestampMs;
      return this;
    }

    /** Whether the values of this hang also go to the stores. */
    public @NotNull Builder setShouldPersist(final boolean shouldPersist) {
      this.shouldPersist = shouldPersist;
      return this;
    }

    public boolean isShouldPersist() {
      return shouldPersist;
    }

    /** Counts one sampling tick and returns the new count. */
    public int increaseSamplingCount() {
      return ++sampleCount;
    }

    public @NotNull Builder addStackTrace(final @NotNull AnrStackTrace stackTrace) {
      stackTraces.add(stackTrace);
      return this;
    }

    public @NotNull Builder addRuntimeStats(final @NotNull RuntimeStatsSnapshot runtimeStats) {
      this.runtimeStats.add(runtimeStats);
      return this;
    }

    public @NotNull Builder setEndedAtMaxRecordingTime(final boolean endedAtMaxRecordingTime) {
      this.endedAtMaxRecordingTime = endedAtMaxRecordingTime;
      return this;
    }

    public @NotNull HangRecord build() {
      return new HangRecord(this);
    }
  }
}
