package io.sentry.android.core;

import static io.sentry.protocol.MeasurementValue.NONE_UNIT;

import android.app.Activity;
import android.util.SparseIntArray;
import androidx.core.app.FrameMetricsAggregator;
import io.sentry.ILogger;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A class that tracks slow and frozen frames using the FrameMetricsAggregator class from
 * androidx.core package. It also checks if the FrameMetricsAggregator class is available at
 * runtime.
 */
public final class ActivityFramesTracker {

  private @Nullable FrameMetricsAggregator frameMetricsAggregator = null;
  private boolean androidXAvailable = true;

  private final @NotNull Map<SentryId, Map<String, @NotNull MeasurementValue>>
      activityMeasurements = new ConcurrentHashMap<>();
  private final @NotNull Map<Activity, FrameCounts> frameCountAtStartSnapshots =
      new WeakHashMap<>();

  public ActivityFramesTracker(final @NotNull LoadClass loadClass, final @Nullable ILogger logger) {
    androidXAvailable =
        loadClass.isClassAvailable("androidx.core.app.FrameMetricsAggregator", logger);
    if (androidXAvailable) {
      frameMetricsAggregator = new FrameMetricsAggregator();
    }
  }

  public ActivityFramesTracker(final @NotNull LoadClass loadClass) {
    this(loadClass, null);
  }

  @TestOnly
  ActivityFramesTracker(final @Nullable FrameMetricsAggregator frameMetricsAggregator) {
    this.frameMetricsAggregator = frameMetricsAggregator;
  }

  private boolean isFrameMetricsAggregatorAvailable() {
    return androidXAvailable && frameMetricsAggregator != null;
  }

  @SuppressWarnings("NullAway")
  public synchronized void addActivity(final @NotNull Activity activity) {
    if (!isFrameMetricsAggregatorAvailable()) {
      return;
    }
    frameMetricsAggregator.add(activity);
    snapshotFrameCountsAtStart(activity);
  }

  private void snapshotFrameCountsAtStart(final @NotNull Activity activity) {
    FrameCounts frameCounts = calculateCurrentFrameCounts();
    if (frameCounts != null) {
      frameCountAtStartSnapshots.put(activity, frameCounts);
    }
  }

  private @Nullable FrameCounts diffFrameCountsAtEnd(final @NotNull Activity activity) {
    @Nullable
    final FrameCounts frameCountsAtStartFromMap = frameCountAtStartSnapshots.remove(activity);
    @NotNull
    final FrameCounts frameCountsAtStart =
        frameCountsAtStartFromMap == null ? new FrameCounts(0, 0, 0) : frameCountsAtStartFromMap;

    @Nullable final FrameCounts frameCountsAtEnd = calculateCurrentFrameCounts();
    if (frameCountsAtEnd == null) {
      return null;
    }

    final int diffTotalFrames = frameCountsAtEnd.totalFrames - frameCountsAtStart.totalFrames;
    final int diffSlowFrames = frameCountsAtEnd.slowFrames - frameCountsAtStart.slowFrames;
    final int diffFrozenFrames = frameCountsAtEnd.frozenFrames - frameCountsAtStart.frozenFrames;

    return new FrameCounts(diffTotalFrames, diffSlowFrames, diffFrozenFrames);
  }

  private @Nullable FrameCounts calculateCurrentFrameCounts() {
    if (!isFrameMetricsAggregatorAvailable()) {
      return null;
    }

    if (frameMetricsAggregator == null) {
      return null;
    }
    final @Nullable SparseIntArray[] framesRates = frameMetricsAggregator.getMetrics();

    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;

    if (framesRates != null && framesRates.length > 0) {
      final SparseIntArray totalIndexArray = framesRates[FrameMetricsAggregator.TOTAL_INDEX];
      if (totalIndexArray != null) {
        for (int i = 0; i < totalIndexArray.size(); i++) {
          int frameTime = totalIndexArray.keyAt(i);
          int numFrames = totalIndexArray.valueAt(i);
          totalFrames += numFrames;
          // hard coded values, its also in the official android docs and frame metrics API
          if (frameTime > 700) {
            // frozen frames, threshold is 700ms
            frozenFrames += numFrames;
          } else if (frameTime > 16) {
            // slow frames, above 16ms, 60 frames/second
            slowFrames += numFrames;
          }
        }
      }
    }

    return new FrameCounts(totalFrames, slowFrames, frozenFrames);
  }

  private static final class FrameCounts {
    private final int totalFrames;
    private final int slowFrames;
    private final int frozenFrames;

    private FrameCounts(final int totalFrames, final int slowFrames, final int frozenFrames) {
      this.totalFrames = totalFrames;
      this.slowFrames = slowFrames;
      this.frozenFrames = frozenFrames;
    }
  }

  @SuppressWarnings("NullAway")
  public synchronized void setMetrics(
      final @NotNull Activity activity, final @NotNull SentryId transactionId) {
    if (!isFrameMetricsAggregatorAvailable()) {
      return;
    }

    try {
      // NOTE: removing an activity does not reset the frame counts, only reset() does
      frameMetricsAggregator.remove(activity);
    } catch (Throwable ignored) {
      // throws IllegalArgumentException when attempting to remove OnFrameMetricsAvailableListener
      // that was never added.
      // there's no contains method.
      // throws NullPointerException when attempting to remove OnFrameMetricsAvailableListener and
      // there was no
      // Observers, See
      // https://android.googlesource.com/platform/frameworks/base/+/140ff5ea8e2d99edc3fbe63a43239e459334c76b
    }

    final @Nullable FrameCounts frameCounts = diffFrameCountsAtEnd(activity);

    if (frameCounts == null
        || (frameCounts.totalFrames == 0
            && frameCounts.slowFrames == 0
            && frameCounts.frozenFrames == 0)) {
      return;
    }

    final MeasurementValue tfValues = new MeasurementValue(frameCounts.totalFrames, NONE_UNIT);
    final MeasurementValue sfValues = new MeasurementValue(frameCounts.slowFrames, NONE_UNIT);
    final MeasurementValue ffValues = new MeasurementValue(frameCounts.frozenFrames, NONE_UNIT);
    final Map<String, @NotNull MeasurementValue> measurements = new HashMap<>();
    measurements.put("frames_total", tfValues);
    measurements.put("frames_slow", sfValues);
    measurements.put("frames_frozen", ffValues);

    activityMeasurements.put(sentryId, measurements);
  }

  @Nullable
  public synchronized Map<String, @NotNull MeasurementValue> takeMetrics(
      final @NotNull SentryId sentryId) {
    if (!isFrameMetricsAggregatorAvailable()) {
      return null;
    }

    final Map<String, @NotNull MeasurementValue> stringMeasurementValueMap =
        activityMeasurements.get(sentryId);
    activityMeasurements.remove(sentryId);
    return stringMeasurementValueMap;
  }

  @SuppressWarnings("NullAway")
  public synchronized void stop() {
    if (isFrameMetricsAggregatorAvailable()) {
      frameMetricsAggregator.stop();
      frameMetricsAggregator.reset();
    }
    activityMeasurements.clear();
  }
}
