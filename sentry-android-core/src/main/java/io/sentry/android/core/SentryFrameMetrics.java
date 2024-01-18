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

  public SentryFrameMetrics() {}

  public SentryFrameMetrics(
      int normalFrameCount,
      int slowFrameCount,
      long slowFrameDelayNanos,
      int frozenFrameCount,
      long frozenFrameDelayNanos) {
    this.normalFrameCount = normalFrameCount;

    this.slowFrameCount = slowFrameCount;
    this.slowFrameDelayNanos = slowFrameDelayNanos;

    this.frozenFrameCount = frozenFrameCount;
    this.frozenFrameDelayNanos = frozenFrameDelayNanos;
  }

  public void addSlowFrame(final long delayNanos) {
    slowFrameDelayNanos += delayNanos;
    slowFrameCount++;
  }

  public void addFrozenFrame(final long delayNanos) {
    frozenFrameDelayNanos += delayNanos;
    frozenFrameCount++;
  }

  public void addNormalFrame() {
    normalFrameCount++;
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

  public void clear() {
    normalFrameCount = 0;

    slowFrameCount = 0;
    slowFrameDelayNanos = 0;

    frozenFrameCount = 0;
    frozenFrameDelayNanos = 0;
  }

  @NotNull
  public SentryFrameMetrics duplicate() {
    return new SentryFrameMetrics(
        normalFrameCount,
        slowFrameCount,
        slowFrameDelayNanos,
        frozenFrameCount,
        frozenFrameDelayNanos);
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
        frozenFrameDelayNanos - other.frozenFrameDelayNanos);
  }

  public boolean containsValidData() {
    // TODO sanity check durations?
    return getTotalFrameCount() > 0;
  }
}
