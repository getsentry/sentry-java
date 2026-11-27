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

  private HangRecord(final @NotNull Builder builder) {
    this.durationMs = builder.durationMs;
    this.startTimestampMs = builder.startTimestampMs;
    this.stackTraces = Collections.unmodifiableList(new ArrayList<>(builder.stackTraces));
  }

  /** The full duration of the hang, also when only its start was sampled. */
  public long getDurationMs() {
    return durationMs;
  }

  /** The start of the hang, as Unix time in milliseconds. */
  public long getStartTimestampMs() {
    return startTimestampMs;
  }

  /**
   * The stacks of the watched thread, in the order they were sampled. They cover only the start of
   * a long hang.
   */
  public @NotNull List<AnrStackTrace> getStackTraces() {
    return stackTraces;
  }

  /** Builds a {@link HangRecord}. */
  public static final class Builder {
    private long durationMs;
    private long startTimestampMs;
    private final @NotNull List<AnrStackTrace> stackTraces = new ArrayList<>();

    public @NotNull Builder setDurationMs(final long durationMs) {
      this.durationMs = durationMs;
      return this;
    }

    /** The start of the hang, as Unix time in milliseconds. */
    public @NotNull Builder setStartTimestampMs(final long startTimestampMs) {
      this.startTimestampMs = startTimestampMs;
      return this;
    }

    public @NotNull Builder addStackTrace(final @NotNull AnrStackTrace stackTrace) {
      stackTraces.add(stackTrace);
      return this;
    }

    public @NotNull HangRecord build() {
      return new HangRecord(this);
    }
  }
}
