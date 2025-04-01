package io.sentry.android.core;

import android.app.Activity;
import android.util.SparseIntArray;
import androidx.core.app.FrameMetricsAggregator;
import io.sentry.ISentryLifecycleToken;
import io.sentry.MeasurementUnit;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.AndroidThreadChecker;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * A class that tracks slow and frozen frames using the FrameMetricsAggregator class from
 * androidx.core package. It also checks if the FrameMetricsAggregator class is available at
 * runtime.
 *
 * <p>If performance-v2 is enabled, frame metrics are recorded using {@link
 * io.sentry.android.core.internal.util.SentryFrameMetricsCollector} via {@link
 * SpanFrameMetricsCollector} instead and this implementation will no-op.
 */
public final class ActivityFramesTracker {

  private @Nullable FrameMetricsAggregator frameMetricsAggregator = null;
  private @NotNull final SentryAndroidOptions options;

  private final @NotNull Map<SentryId, Map<String, @NotNull MeasurementValue>>
      activityMeasurements = new ConcurrentHashMap<>();
  private final @NotNull Map<Activity, FrameCounts> frameCountAtStartSnapshots =
      new WeakHashMap<>();

  private final @NotNull MainLooperHandler handler;
  protected @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public ActivityFramesTracker(
      final @NotNull io.sentry.util.LoadClass loadClass,
      final @NotNull SentryAndroidOptions options,
      final @NotNull MainLooperHandler handler) {

    final boolean androidXAvailable =
        loadClass.isClassAvailable("androidx.core.app.FrameMetricsAggregator", options.getLogger());

    if (androidXAvailable) {
      frameMetricsAggregator = new FrameMetricsAggregator();
    }
    this.options = options;
    this.handler = handler;
  }

  public ActivityFramesTracker(
      final @NotNull io.sentry.util.LoadClass loadClass,
      final @NotNull SentryAndroidOptions options) {
    this(loadClass, options, new MainLooperHandler());
  }

  @TestOnly
  ActivityFramesTracker(
      final @NotNull io.sentry.util.LoadClass loadClass,
      final @NotNull SentryAndroidOptions options,
      final @NotNull MainLooperHandler handler,
      final @Nullable FrameMetricsAggregator frameMetricsAggregator) {

    this(loadClass, options, handler);
    this.frameMetricsAggregator = frameMetricsAggregator;
  }

  @VisibleForTesting
  public boolean isFrameMetricsAggregatorAvailable() {
    return frameMetricsAggregator != null
        && options.isEnableFramesTracking()
        && !options.isEnablePerformanceV2();
  }

  @SuppressWarnings("NullAway")
  public void addActivity(final @NotNull Activity activity) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (!isFrameMetricsAggregatorAvailable()) {
        return;
      }

      runSafelyOnUiThread(() -> frameMetricsAggregator.add(activity), "FrameMetricsAggregator.add");
      snapshotFrameCountsAtStart(activity);
    }
  }

  private void snapshotFrameCountsAtStart(final @NotNull Activity activity) {
    FrameCounts frameCounts = calculateCurrentFrameCounts();
    if (frameCounts != null) {
      frameCountAtStartSnapshots.put(activity, frameCounts);
    }
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

  @SuppressWarnings("NullAway")
  public void setMetrics(final @NotNull Activity activity, final @NotNull SentryId transactionId) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (!isFrameMetricsAggregatorAvailable()) {
        return;
      }

      // NOTE: removing an activity does not reset the frame counts, only reset() does
      // throws IllegalArgumentException when attempting to remove
      // OnFrameMetricsAvailableListener
      // that was never added.
      // there's no contains method.
      // throws NullPointerException when attempting to remove
      // OnFrameMetricsAvailableListener and
      // there was no
      // Observers, See
      // https://android.googlesource.com/platform/frameworks/base/+/140ff5ea8e2d99edc3fbe63a43239e459334c76b
      runSafelyOnUiThread(() -> frameMetricsAggregator.remove(activity), null);

      final @Nullable FrameCounts frameCounts = diffFrameCountsAtEnd(activity);

      if (frameCounts == null
          || (frameCounts.totalFrames == 0
              && frameCounts.slowFrames == 0
              && frameCounts.frozenFrames == 0)) {
        return;
      }

      final MeasurementValue tfValues =
          new MeasurementValue(frameCounts.totalFrames, MeasurementUnit.NONE);
      final MeasurementValue sfValues =
          new MeasurementValue(frameCounts.slowFrames, MeasurementUnit.NONE);
      final MeasurementValue ffValues =
          new MeasurementValue(frameCounts.frozenFrames, MeasurementUnit.NONE);
      final Map<String, @NotNull MeasurementValue> measurements = new HashMap<>();
      measurements.put(MeasurementValue.KEY_FRAMES_TOTAL, tfValues);
      measurements.put(MeasurementValue.KEY_FRAMES_SLOW, sfValues);
      measurements.put(MeasurementValue.KEY_FRAMES_FROZEN, ffValues);

      activityMeasurements.put(transactionId, measurements);
    }
  }

  private @Nullable FrameCounts diffFrameCountsAtEnd(final @NotNull Activity activity) {
    @Nullable final FrameCounts frameCountsAtStart = frameCountAtStartSnapshots.remove(activity);
    if (frameCountsAtStart == null) {
      return null;
    }

    @Nullable final FrameCounts frameCountsAtEnd = calculateCurrentFrameCounts();
    if (frameCountsAtEnd == null) {
      return null;
    }

    final int diffTotalFrames = frameCountsAtEnd.totalFrames - frameCountsAtStart.totalFrames;
    final int diffSlowFrames = frameCountsAtEnd.slowFrames - frameCountsAtStart.slowFrames;
    final int diffFrozenFrames = frameCountsAtEnd.frozenFrames - frameCountsAtStart.frozenFrames;

    return new FrameCounts(diffTotalFrames, diffSlowFrames, diffFrozenFrames);
  }

  @Nullable
  public Map<String, @NotNull MeasurementValue> takeMetrics(final @NotNull SentryId transactionId) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (!isFrameMetricsAggregatorAvailable()) {
        return null;
      }

      final Map<String, @NotNull MeasurementValue> stringMeasurementValueMap =
          activityMeasurements.get(transactionId);
      activityMeasurements.remove(transactionId);
      return stringMeasurementValueMap;
    }
  }

  @SuppressWarnings("NullAway")
  public void stop() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (isFrameMetricsAggregatorAvailable()) {
        runSafelyOnUiThread(() -> frameMetricsAggregator.stop(), "FrameMetricsAggregator.stop");
        frameMetricsAggregator.reset();
      }
      activityMeasurements.clear();
    }
  }

  private void runSafelyOnUiThread(final Runnable runnable, final String tag) {
    try {
      if (AndroidThreadChecker.getInstance().isMainThread()) {
        runnable.run();
      } else {
        handler.post(
            () -> {
              try {
                runnable.run();
              } catch (Throwable ignored) {
                if (tag != null) {
                  if (options.getLogger().isEnabled(SentryLevel.WARNING)) {
                    options.getLogger().log(SentryLevel.WARNING, "Failed to execute " + tag);
                  }
                }
              }
            });
      }
    } catch (Throwable ignored) {
      if (tag != null) {
        if (options.getLogger().isEnabled(SentryLevel.WARNING)) {
          options.getLogger().log(SentryLevel.WARNING, "Failed to execute " + tag);
        }
      }
    }
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
}
