package io.sentry.android.core.hang;

import io.sentry.android.core.anr.AnrStackTrace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The collected data of one hang interval that ended with a record. */
@ApiStatus.Internal
public final class HangRecord {

  private final long durationMs;
  private final long startTimestampMs;
  private final @NotNull List<AnrStackTrace> stackTraces;
  private final @Nullable Map<String, Object> runtimeStats;
  private final boolean endedAtMaxRecordingTime;

  private HangRecord(final @NotNull Builder builder) {
    this.durationMs = builder.durationMs;
    this.startTimestampMs = builder.startTimestampMs;
    this.stackTraces = Collections.unmodifiableList(new ArrayList<>(builder.stackTraces));
    this.runtimeStats = builder.runtimeStats;
    this.endedAtMaxRecordingTime = builder.endedAtMaxRecordingTime;
  }

  public long getDurationMs() {
    return durationMs;
  }

  /** The start of the interval, as Unix time in milliseconds. */
  public long getStartTimestampMs() {
    return startTimestampMs;
  }

  /** The stacks of the watched thread, in the order they were sampled. */
  public @NotNull List<AnrStackTrace> getStackTraces() {
    return stackTraces;
  }

  /** Null if no collector recorded runtime stats for this interval. */
  public @Nullable Map<String, Object> getRuntimeStats() {
    return runtimeStats;
  }

  /**
   * Whether the interval was cut at the maximum recording time. The watched thread is then still
   * blocked when the record is given to the listeners.
   */
  public boolean hasEndedAtMaxRecordingTime() {
    return endedAtMaxRecordingTime;
  }

  public static final class Builder {
    private final long durationMs;
    private final long startTimestampMs;
    private final @NotNull List<AnrStackTrace> stackTraces = new ArrayList<>();
    private @Nullable Map<String, Object> runtimeStats;
    private boolean endedAtMaxRecordingTime;

    public Builder(final long durationMs, final long startTimestampMs) {
      this.durationMs = durationMs;
      this.startTimestampMs = startTimestampMs;
    }

    public @NotNull Builder addStackTrace(final @NotNull AnrStackTrace stackTrace) {
      stackTraces.add(stackTrace);
      return this;
    }

    public @NotNull Builder setRuntimeStats(final @Nullable Map<String, Object> runtimeStats) {
      this.runtimeStats = runtimeStats;
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
