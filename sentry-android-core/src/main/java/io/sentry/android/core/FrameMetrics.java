package io.sentry.android.core;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
final class FrameMetrics {

  private int normalFrameCount;
  private int slowFrameCount;
  private int frozenFrameCount;

  private long slowFrameDuration;
  private long frozenFrameDuration;

  public FrameMetrics() {}

  public FrameMetrics(
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

  public void addSlowFrame(long duration) {
    slowFrameDuration += duration;
    slowFrameCount++;
  }

  public void addFrozenFrame(long duration) {
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

  public FrameMetrics duplicate() {
    return new FrameMetrics(
        normalFrameCount, slowFrameCount, slowFrameDuration, frozenFrameCount, frozenFrameDuration);
  }

  /**
   * @param other the other frame metrics to compare to, usually the older one
   * @return the difference between two frame metrics (this minus other)
   */
  public FrameMetrics diffTo(FrameMetrics other) {
    return new FrameMetrics(
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
