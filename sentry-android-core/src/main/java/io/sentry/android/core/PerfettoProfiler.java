package io.sentry.android.core;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ProfilingManager;
import android.os.ProfilingResult;
import androidx.annotation.RequiresApi;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.SentryNanotimeDate;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.profilemeasurements.ProfileMeasurement;
import io.sentry.profilemeasurements.ProfileMeasurementValue;
import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
public class PerfettoProfiler {

  private static final long RESULT_TIMEOUT_SECONDS = 5;

  // Bundle keys matching ProfilingManager / AndroidX Profiling.kt constants
  private static final String KEY_DURATION_MS = "KEY_DURATION_MS";
  private static final String KEY_FREQUENCY_HZ = "KEY_FREQUENCY_HZ";

  private final @NotNull Context context;
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;
  private final @NotNull ILogger logger;
  private final int profilingTracesHz;
  private @Nullable CancellationSignal cancellationSignal = null;
  private @Nullable String frameMetricsCollectorId;
  private volatile boolean isRunning = false;
  private @Nullable ProfilingResult profilingResult = null;
  private @Nullable CountDownLatch resultLatch = null;

  private final @NotNull ArrayDeque<ProfileMeasurementValue> slowFrameRenderMeasurements =
      new ArrayDeque<>();
  private final @NotNull ArrayDeque<ProfileMeasurementValue> frozenFrameRenderMeasurements =
      new ArrayDeque<>();
  private final @NotNull ArrayDeque<ProfileMeasurementValue> screenFrameRateMeasurements =
      new ArrayDeque<>();
  private final @NotNull Map<String, ProfileMeasurement> measurementsMap = new HashMap<>();

  public PerfettoProfiler(
      final @NotNull Context context,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector,
      final @NotNull ILogger logger,
      final int profilingTracesHz) {
    this.context = context;
    this.frameMetricsCollector = frameMetricsCollector;
    this.logger = logger;
    this.profilingTracesHz = profilingTracesHz;
  }

  public @Nullable AndroidProfiler.ProfileStartData start(final long durationMs) {
    if (isRunning) {
      logger.log(SentryLevel.WARNING, "Perfetto profiling has already started...");
      return null;
    }

    final @Nullable ProfilingManager profilingManager =
        (ProfilingManager) context.getSystemService(Context.PROFILING_SERVICE);
    if (profilingManager == null) {
      logger.log(SentryLevel.WARNING, "ProfilingManager is not available.");
      return null;
    }

    measurementsMap.clear();
    slowFrameRenderMeasurements.clear();
    frozenFrameRenderMeasurements.clear();
    screenFrameRateMeasurements.clear();

    cancellationSignal = new CancellationSignal();
    resultLatch = new CountDownLatch(1);
    profilingResult = null;

    final Consumer<ProfilingResult> listener =
        result -> {
          profilingResult = result;
          if (resultLatch != null) {
            resultLatch.countDown();
          }
        };

    final Bundle params = new Bundle();
    params.putInt(KEY_DURATION_MS, (int) durationMs);
    params.putInt(KEY_FREQUENCY_HZ, profilingTracesHz);

    try {
      profilingManager.requestProfiling(
          ProfilingManager.PROFILING_TYPE_STACK_SAMPLING,
          params,
          "sentry-profiling",
          cancellationSignal,
          Runnable::run,
          listener);
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Failed to request Perfetto profiling.", e);
      cancellationSignal = null;
      resultLatch = null;
      return null;
    }

    frameMetricsCollectorId =
        frameMetricsCollector.startCollection(
            new SentryFrameMetricsCollector.FrameMetricsCollectorListener() {
              float lastRefreshRate = 0;

              @Override
              public void onFrameMetricCollected(
                  final long frameStartNanos,
                  final long frameEndNanos,
                  final long durationNanos,
                  final long delayNanos,
                  final boolean isSlow,
                  final boolean isFrozen,
                  final float refreshRate) {
                final long timestampNanos = new SentryNanotimeDate().nanoTimestamp();
                if (isFrozen) {
                  frozenFrameRenderMeasurements.addLast(
                      new ProfileMeasurementValue(frameEndNanos, durationNanos, timestampNanos));
                } else if (isSlow) {
                  slowFrameRenderMeasurements.addLast(
                      new ProfileMeasurementValue(frameEndNanos, durationNanos, timestampNanos));
                }
                if (refreshRate != lastRefreshRate) {
                  lastRefreshRate = refreshRate;
                  screenFrameRateMeasurements.addLast(
                      new ProfileMeasurementValue(frameEndNanos, refreshRate, timestampNanos));
                }
              }
            });

    isRunning = true;
    return new AndroidProfiler.ProfileStartData(
        System.nanoTime(),
        android.os.Process.getElapsedCpuTime(),
        io.sentry.DateUtils.getCurrentDateTime());
  }

  public @Nullable AndroidProfiler.ProfileEndData endAndCollect() {
    if (!isRunning) {
      logger.log(SentryLevel.WARNING, "Perfetto profiler not running");
      return null;
    }
    isRunning = false;

    frameMetricsCollector.stopCollection(frameMetricsCollectorId);

    // Cancel the profiling to signal the system to finish
    if (cancellationSignal != null) {
      cancellationSignal.cancel();
      cancellationSignal = null;
    }

    // Wait for the profiling result
    if (resultLatch != null) {
      try {
        if (!resultLatch.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          logger.log(SentryLevel.WARNING, "Timed out waiting for Perfetto profiling result.");
          return null;
        }
      } catch (InterruptedException e) {
        logger.log(SentryLevel.WARNING, "Interrupted while waiting for Perfetto profiling result.");
        Thread.currentThread().interrupt();
        return null;
      }
    }

    if (profilingResult == null) {
      logger.log(SentryLevel.WARNING, "Perfetto profiling result is null.");
      return null;
    }

    final int errorCode = profilingResult.getErrorCode();
    if (errorCode != ProfilingResult.ERROR_NONE) {
      final @Nullable String errorMessage = profilingResult.getErrorMessage();
      logger.log(
          SentryLevel.WARNING,
          "Perfetto profiling failed with error code %d: %s",
          errorCode,
          errorMessage);
      return null;
    }

    final @Nullable String resultFilePath = profilingResult.getResultFilePath();
    if (resultFilePath == null) {
      logger.log(SentryLevel.WARNING, "Perfetto profiling result file path is null.");
      return null;
    }

    final File traceFile = new File(resultFilePath);
    if (!traceFile.exists() || traceFile.length() == 0) {
      logger.log(SentryLevel.WARNING, "Perfetto trace file does not exist or is empty.");
      return null;
    }

    if (!slowFrameRenderMeasurements.isEmpty()) {
      measurementsMap.put(
          ProfileMeasurement.ID_SLOW_FRAME_RENDERS,
          new ProfileMeasurement(ProfileMeasurement.UNIT_NANOSECONDS, slowFrameRenderMeasurements));
    }
    if (!frozenFrameRenderMeasurements.isEmpty()) {
      measurementsMap.put(
          ProfileMeasurement.ID_FROZEN_FRAME_RENDERS,
          new ProfileMeasurement(
              ProfileMeasurement.UNIT_NANOSECONDS, frozenFrameRenderMeasurements));
    }
    if (!screenFrameRateMeasurements.isEmpty()) {
      measurementsMap.put(
          ProfileMeasurement.ID_SCREEN_FRAME_RATES,
          new ProfileMeasurement(ProfileMeasurement.UNIT_HZ, screenFrameRateMeasurements));
    }

    return new AndroidProfiler.ProfileEndData(
        System.nanoTime(),
        android.os.Process.getElapsedCpuTime(),
        false,
        traceFile,
        measurementsMap);
  }

  boolean isRunning() {
    return isRunning;
  }
}
