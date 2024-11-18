package io.sentry.android.core;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
final class SentryFrameMetrics {

  private int slowFrameCount;
  private int frozenFrameCount;

  private long slowFrameDelayNanos;
  private long frozenFrameDelayNanos;

  private long totalDurationNanos;

  public SentryFrameMetrics() {}

  public SentryFrameMetrics(
      final int slowFrameCount,
      final long slowFrameDelayNanos,
      final int frozenFrameCount,
      final long frozenFrameDelayNanos,
      final long totalDurationNanos) {
    this.slowFrameCount = slowFrameCount;
    this.slowFrameDelayNanos = slowFrameDelayNanos;

    this.frozenFrameCount = frozenFrameCount;
    this.frozenFrameDelayNanos = frozenFrameDelayNanos;
    this.totalDurationNanos = totalDurationNanos;
  }

  public void addFrame(
      final long durationNanos,
      final long delayNanos,
      final boolean isSlow,
      final boolean isFrozen) {
    totalDurationNanos += durationNanos;
    if (isFrozen) {
      frozenFrameDelayNanos += delayNanos;
      frozenFrameCount += 1;
    } else if (isSlow) {
      slowFrameDelayNanos += delayNanos;
      slowFrameCount += 1;
    }
  }

  public int getSlowFrameCount() {
    return slowFrameCount;
  }

  public int getFrozenFrameCount() {
    return frozenFrameCount;
  }

  public long getSlowFrameDelayNanos() {
    return slowFrameDelayNanos;
  }

  public long getFrozenFrameDelayNanos() {
    return frozenFrameDelayNanos;
  }

  /** Returns the sum of the slow and frozen frames. */
  public int getSlowFrozenFrameCount() {
    return slowFrameCount + frozenFrameCount;
  }

  public long getTotalDurationNanos() {
    return totalDurationNanos;
  }

  public void clear() {
    slowFrameCount = 0;
    slowFrameDelayNanos = 0;

    frozenFrameCount = 0;
    frozenFrameDelayNanos = 0;

    totalDurationNanos = 0;
  }

  @NotNull
  public SentryFrameMetrics duplicate() {
    return new SentryFrameMetrics(
        slowFrameCount,
        slowFrameDelayNanos,
        frozenFrameCount,
        frozenFrameDelayNanos,
        totalDurationNanos);
  }

  /**
   * @param other the other frame metrics to compare to, usually the older one
   * @return the difference between two frame metrics (this minus other)
   */
  @NotNull
  public SentryFrameMetrics diffTo(final @NotNull SentryFrameMetrics other) {
    return new SentryFrameMetrics(
        slowFrameCount - other.slowFrameCount,
        slowFrameDelayNanos - other.slowFrameDelayNanos,
        frozenFrameCount - other.frozenFrameCount,
        frozenFrameDelayNanos - other.frozenFrameDelayNanos,
        totalDurationNanos - other.totalDurationNanos);
  }

  /**
   * @return true if the frame metrics contain valid data, meaning all values are greater or equal
   *     to 0
   */
  public boolean containsValidData() {
    return slowFrameCount >= 0
        && slowFrameDelayNanos >= 0
        && frozenFrameCount >= 0
        && frozenFrameDelayNanos >= 0
        && totalDurationNanos >= 0;
  }
}
