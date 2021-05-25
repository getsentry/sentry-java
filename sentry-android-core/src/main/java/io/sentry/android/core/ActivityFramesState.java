package io.sentry.android.core;

import android.app.Activity;
import android.util.SparseIntArray;
import androidx.core.app.FrameMetricsAggregator;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ActivityFramesState {

  private static final @NotNull ActivityFramesState instance = new ActivityFramesState();

  private final @NotNull FrameMetricsAggregator frameMetricsAggregator =
      new FrameMetricsAggregator();

  private ActivityFramesState() {}

  static @NotNull ActivityFramesState getInstance() {
    return instance;
  }

  private final @NotNull Map<SentryId, Map<String, @NotNull MeasurementValue>> activityMeasurements =
          Collections.synchronizedMap(new WeakHashMap<>());

  void addActivity(final @NotNull Activity activity) {
    frameMetricsAggregator.add(activity);
  }

//  @Nullable
//  Map<String, @NotNull MeasurementValue> removeActivity(final @NotNull Activity activity) {
//    final SparseIntArray[] framesRates = frameMetricsAggregator.remove(activity);
//    return getMetrics(framesRates);
//  }

//  @Nullable
  void setMetrics(final @NotNull Activity activity, final @NotNull SentryId sentryId) {
    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;

    // I noticed that when the 1st screen kicks in and finishes rendering, the `getMetrics` still
    // returns nothing, but the next transaction contains the accumulated value of both of them.

    final SparseIntArray[] framesRates = frameMetricsAggregator.remove(activity);
    //    final SparseIntArray[] framesRates = frameMetricsAggregator.reset();
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
          }
          if (frameTime > 16) {
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
  Map<String, @NotNull MeasurementValue> getMetrics(final @NotNull SentryId sentryId) {
    return activityMeasurements.get(sentryId);
  }

  void close() {
    frameMetricsAggregator.stop();
  }
}
