package io.sentry.android.core;

import android.app.Activity;
import android.util.SparseIntArray;
import androidx.core.app.FrameMetricsAggregator;
import io.sentry.protocol.MeasurementValue;
import java.util.HashMap;
import java.util.Map;
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

  void addActivity(final @NotNull Activity activity) {
    frameMetricsAggregator.add(activity);
  }

  void removeActivity(final @NotNull Activity activity) {
    frameMetricsAggregator.remove(activity);
  }

  @Nullable
  Map<String, @NotNull MeasurementValue> getMetrics() {
    int totalFrames = 0;
    int slowFrames = 0;
    int frozenFrames = 0;
    // TODO: or frameMetricsAggregator.reset(); ?
    final SparseIntArray[] framesRates = frameMetricsAggregator.getMetrics();
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
      return null;
    }

    final MeasurementValue tfValues = new MeasurementValue(totalFrames);
    final MeasurementValue sfValues = new MeasurementValue(slowFrames);
    final MeasurementValue ffValues = new MeasurementValue(frozenFrames);
    final Map<String, @NotNull MeasurementValue> measurements = new HashMap<>();
    measurements.put("frames_total", tfValues);
    measurements.put("frames_slow", sfValues);
    measurements.put("frames_frozen", ffValues);
    return measurements;
  }

  void close() {
    frameMetricsAggregator.stop();
  }
}
