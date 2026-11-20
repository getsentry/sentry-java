package io.sentry.android.core.hang;

import io.sentry.android.core.anr.AnrStackTrace;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** The collected data of one hang interval that ended with a record. */
@ApiStatus.Internal
public final class HangRecord {

  private final long durationMs;
  private final long startTimestampMs;
  private final @NotNull List<AnrStackTrace> samples;
  private final boolean endedAtMaxRecordingTime;

  private HangRecord(final @NotNull Builder builder) {
    this.durationMs = builder.durationMs;
    this.startTimestampMs = builder.startTimestampMs;
    this.samples = builder.samples;
    this.endedAtMaxRecordingTime = builder.endedAtMaxRecordingTime;
  }

  public long getDurationMs() {
    return durationMs;
  }

  /** The start of the interval, as Unix time in milliseconds. */
  public long getStartTimestampMs() {
    return startTimestampMs;
  }

  public @NotNull List<AnrStackTrace> getSamples() {
    return samples;
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
    private @NotNull List<AnrStackTrace> samples = Collections.emptyList();
    private boolean endedAtMaxRecordingTime;

    public Builder(final long durationMs, final long startTimestampMs) {
      this.durationMs = durationMs;
      this.startTimestampMs = startTimestampMs;
    }

    public @NotNull Builder setSamples(final @NotNull List<AnrStackTrace> samples) {
      this.samples = samples;
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
