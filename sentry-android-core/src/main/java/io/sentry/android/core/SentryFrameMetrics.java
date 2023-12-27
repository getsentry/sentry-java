package io.sentry.android.core;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
final class SentryFrameMetrics {

  private int normalFrameCount;
  private int slowFrameCount;
  private int frozenFrameCount;

  private long slowFrameDuration;
  private long frozenFrameDuration;

  public SentryFrameMetrics() {}

  public SentryFrameMetrics(
      int normalFrameCount,
      int slowFrameCount,
      long slowFrameDuration,
      int frozenFrameCount,
      long frozenFrameDuration) {
    this.normalFrameCount = normalFrameCount;

    this.slowFrameCount = slowFrameCount;
    this.slowFrameDuration = slowFrameDuration;

    this.frozenFrameCount = frozenFrameCount;
    this.frozenFrameDuration = frozenFrameDuration;
  }

  public void addSlowFrame(final long duration) {
    slowFrameDuration += duration;
    slowFrameCount++;
  }

  public void addFrozenFrame(final long duration) {
    frozenFrameDuration += duration;
    frozenFrameCount++;
  }

  public void addNormalFrame(long duration) {
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

  public long getSlowFrameDuration() {
    return slowFrameDuration;
  }

  public long getFrozenFrameDuration() {
    return frozenFrameDuration;
  }

  public int getTotalFrameCount() {
    return normalFrameCount + slowFrameCount + frozenFrameCount;
  }

  public void clear() {
    normalFrameCount = 0;

    slowFrameCount = 0;
    slowFrameDuration = 0;

    frozenFrameCount = 0;
    frozenFrameDuration = 0;
  }

  @NotNull
  public SentryFrameMetrics duplicate() {
    return new SentryFrameMetrics(
        normalFrameCount, slowFrameCount, slowFrameDuration, frozenFrameCount, frozenFrameDuration);
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
        slowFrameDuration - other.slowFrameDuration,
        frozenFrameCount - other.frozenFrameCount,
        frozenFrameDuration - other.frozenFrameDuration);
  }

  public boolean containsValidData() {
    // TODO sanity check durations?
    return getTotalFrameCount() > 0;
  }
}
