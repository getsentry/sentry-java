package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class FrameMetrics {
  private int totalFrameCount;
  private int slowFrameCount;
  private int frozenFrameCount;

  private long slowFrameDuration;
  private long frozenFrameDuration;

  public FrameMetrics() {}

  public void incrementTotalFrameCount() {
    totalFrameCount++;
  }

  public void incrementSlowFrameCount() {
    slowFrameCount++;
  }

  public void incrementFrozenFrameCount() {
    frozenFrameCount++;
  }

  public void addSlowFrameDuration(long duration) {
    slowFrameDuration += duration;
  }

  public void addFrozenFrameDuration(long duration) {
    frozenFrameDuration += duration;
  }

  public int getTotalFrameCount() {
    return totalFrameCount;
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
}
