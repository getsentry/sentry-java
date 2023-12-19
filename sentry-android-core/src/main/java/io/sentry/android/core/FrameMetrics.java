package io.sentry.android.core;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
final class FrameMetrics {

  private int fastFrameCount;
  private int slowFrameCount;
  private int frozenFrameCount;

  private long slowFrameDuration;
  private long frozenFrameDuration;
  private long fastFrameDuration;

  public FrameMetrics() {}

  public FrameMetrics(
      int fastFrameCount,
      int slowFrameCount,
      int frozenFrameCount,
      long fastFrameDuration,
      long slowFrameDuration,
      long frozenFrameDuration) {
    this.fastFrameCount = fastFrameCount;
    this.slowFrameCount = slowFrameCount;
    this.frozenFrameCount = frozenFrameCount;

    this.fastFrameDuration = fastFrameDuration;
    this.slowFrameDuration = slowFrameDuration;
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

  public void addFastFrame(long duration) {
    fastFrameDuration += duration;
    fastFrameCount++;
  }

  public int getFastFrameCount() {
    return fastFrameCount;
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
    return fastFrameCount + slowFrameCount + frozenFrameCount;
  }

  public void clear() {
    fastFrameCount = 0;
    slowFrameCount = 0;
    frozenFrameCount = 0;
    slowFrameDuration = 0;
    frozenFrameDuration = 0;
  }

  public FrameMetrics duplicate() {
    return new FrameMetrics(
        fastFrameCount,
        slowFrameCount,
        frozenFrameCount,
        fastFrameDuration,
        slowFrameDuration,
        frozenFrameDuration);
  }

  /**
   * @param other the other frame metrics to compare to, usually the older one
   * @return the difference between two frame metrics (this minus other)
   */
  public FrameMetrics diffTo(FrameMetrics other) {
    return new FrameMetrics(
        fastFrameCount - other.fastFrameCount,
        slowFrameCount - other.slowFrameCount,
        frozenFrameCount - other.frozenFrameCount,
        fastFrameDuration - other.fastFrameDuration,
        slowFrameDuration - other.slowFrameDuration,
        frozenFrameDuration - other.frozenFrameDuration);
  }

  public boolean containsValidData() {
    // TODO sanity check durations?
    return getTotalFrameCount() > 0;
  }
}
