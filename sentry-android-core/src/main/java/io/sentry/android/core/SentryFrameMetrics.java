package io.sentry.android.core;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
final class SentryFrameMetrics {

  private int normalFrameCount;
  private int slowFrameCount;
  private int frozenFrameCount;

  private long slowFrameDelayNanos;
  private long frozenFrameDelayNanos;

  private long totalDurationNanos;

  public SentryFrameMetrics() {}

  public SentryFrameMetrics(
      final int normalFrameCount,
      final int slowFrameCount,
      final long slowFrameDelayNanos,
      final int frozenFrameCount,
      final long frozenFrameDelayNanos,
      final long totalDurationNanos) {

    this.normalFrameCount = normalFrameCount;

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
    } else {
      normalFrameCount += 1;
    }
  }

  public int getNormalFrameCount() {
    return normalFrameCount;
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

  public int getTotalFrameCount() {
    return normalFrameCount + slowFrameCount + frozenFrameCount;
  }

  public long getTotalDurationNanos() {
    return totalDurationNanos;
  }

  public void clear() {
    normalFrameCount = 0;

    slowFrameCount = 0;
    slowFrameDelayNanos = 0;

    frozenFrameCount = 0;
    frozenFrameDelayNanos = 0;

    totalDurationNanos = 0;
  }

  @NotNull
  public SentryFrameMetrics duplicate() {
    return new SentryFrameMetrics(
        normalFrameCount,
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
        normalFrameCount - other.normalFrameCount,
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
    return normalFrameCount >= 0
        && slowFrameCount >= 0
        && slowFrameDelayNanos >= 0
        && frozenFrameCount >= 0
        && frozenFrameDelayNanos >= 0
        && totalDurationNanos >= 0;
  }
}
