package io.sentry.android.core;

import android.app.Activity;
import android.util.SparseIntArray;
import androidx.core.app.FrameMetricsAggregator;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class ActivityFramesTracker {

  private @Nullable FrameMetricsAggregator frameMetricsAggregator = null;
  private boolean androidXAvailable = true;

  private final @NotNull Map<SentryId, Map<String, @NotNull MeasurementValue>>
      activityMeasurements = new ConcurrentHashMap<>();

  ActivityFramesTracker(final @NotNull LoadClass loadClass) {
    androidXAvailable = checkAndroidXAvailability(loadClass);
    if (androidXAvailable) {
      frameMetricsAggregator = new FrameMetricsAggregator();
    }
  }

  @TestOnly
  ActivityFramesTracker(final @Nullable FrameMetricsAggregator frameMetricsAggregator) {
    this.frameMetricsAggregator = frameMetricsAggregator;
  }

  private static boolean checkAndroidXAvailability(final @NotNull LoadClass loadClass) {
    try {
      loadClass.loadClass("androidx.core.app.FrameMetricsAggregator");
      return true;
    } catch (ClassNotFoundException ignored) {
      // androidx.core isn't available.
      return false;
    }
  }

  private boolean isFrameMetricsAggregatorAvailable() {
    return androidXAvailable && frameMetricsAggregator != null;
  }

  @SuppressWarnings("NullAway")
  synchronized void addActivity(final @NotNull Activity activity) {
    if (!isFrameMetricsAggregatorAvailable()) {
      return;
    }
    frameMetricsAggregator.add(activity);
  }

  @SuppressWarnings("NullAway")
  synchronized void setMetrics(final @NotNull Activity activity, final @NotNull SentryId sentryId) {
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

    final MeasurementValue tfValues = new MeasurementValue(totalFrames);
    final MeasurementValue sfValues = new MeasurementValue(slowFrames);
    final MeasurementValue ffValues = new MeasurementValue(frozenFrames);
    final Map<String, @NotNull MeasurementValue> measurements = new HashMap<>();
    measurements.put("frames_total", tfValues);
    measurements.put("frames_slow", sfValues);
    measurements.put("frames_frozen", ffValues);

    activityMeasurements.put(sentryId, measurements);
  }

  @Nullable
  synchronized Map<String, @NotNull MeasurementValue> takeMetrics(
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
  synchronized void stop() {
    if (isFrameMetricsAggregatorAvailable()) {
      frameMetricsAggregator.stop();
    }
    activityMeasurements.clear();
  }
}
