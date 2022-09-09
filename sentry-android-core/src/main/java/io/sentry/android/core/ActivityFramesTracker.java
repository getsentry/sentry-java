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
  }

  @SuppressWarnings("NullAway")
  public synchronized void setMetrics(
      final @NotNull Activity activity, final @NotNull SentryId sentryId) {
    if (!isFrameMetricsAggregatorAvailable()) {
      return;
    }

    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;

    SparseIntArray[] framesRates = null;
    try {
      framesRates = frameMetricsAggregator.remove(activity);
    } catch (Throwable ignored) {
      // throws IllegalArgumentException when attempting to remove OnFrameMetricsAvailableListener
      // that was never added.
      // there's no contains method.
      // throws NullPointerException when attempting to remove OnFrameMetricsAvailableListener and
      // there was no
      // Observers, See
      // https://android.googlesource.com/platform/frameworks/base/+/140ff5ea8e2d99edc3fbe63a43239e459334c76b
    }

    if (framesRates != null) {
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

    if (totalFrames == 0 && slowFrames == 0 && frozenFrames == 0) {
      return;
    }

    final MeasurementValue tfValues = new MeasurementValue(totalFrames, NONE_UNIT);
    final MeasurementValue sfValues = new MeasurementValue(slowFrames, NONE_UNIT);
    final MeasurementValue ffValues = new MeasurementValue(frozenFrames, NONE_UNIT);
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
    }
    activityMeasurements.clear();
  }
}
