package io.sentry.android.core;

import org.jetbrains.annotations.ApiStatus;

/** Result of querying frame delay for a given time range. */
@ApiStatus.Internal
public final class SentryFramesDelayResult {

  private final double delaySeconds;
  private final int framesContributingToDelayCount;

  public SentryFramesDelayResult(
      final double delaySeconds, final int framesContributingToDelayCount) {
    this.delaySeconds = delaySeconds;
    this.framesContributingToDelayCount = framesContributingToDelayCount;
  }

  /**
   * @return the total frame delay in seconds, or -1 if incalculable (e.g. no frame data available)
   */
  public double getDelaySeconds() {
    return delaySeconds;
  }

  /**
   * @return the number of frames that contributed to the delay (slow + frozen frames)
   */
  public int getFramesContributingToDelayCount() {
    return framesContributingToDelayCount;
  }
}
