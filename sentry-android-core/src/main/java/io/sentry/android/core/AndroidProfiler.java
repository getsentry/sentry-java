package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.sentry.CpuCollectionData;
import io.sentry.MemoryCollectionData;
import io.sentry.PerformanceCollectionData;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.profilemeasurements.ProfileMeasurement;
import io.sentry.profilemeasurements.ProfileMeasurementValue;

@ApiStatus.Internal
public class AndroidProfiler {
  public static class ProfileStartData {
    public final long startNanos;
    public final long startCpuMillis;

    public ProfileStartData(
      final long startNanos,
      final long startCpuMillis) {
      this.startNanos = startNanos;
      this.startCpuMillis = startCpuMillis;
    }
  }

  public static class ProfileEndData {
    public final long endNanos;
    public final long endCpuMillis;
    public final @NotNull File traceFile;
    public final @NotNull Map<String, ProfileMeasurement> measurementsMap;

    public ProfileEndData(
      long endNanos,
      long endCpuMillis,
      @NotNull File traceFile,
      @NotNull Map<String, ProfileMeasurement> measurementsMap) {
      this.endNanos = endNanos;
      this.traceFile = traceFile;
      this.endCpuMillis = endCpuMillis;
      this.measurementsMap = measurementsMap;
    }
  }

  /**
   * This appears to correspond to the buffer size of the data part of the file, excluding the key
   * part. Once the buffer is full, new records are ignored, but the resulting trace file will be
   * valid.
   *
   * <p>30 second traces can require a buffer of a few MB. 8MB is the default buffer size for
   * [Debug.startMethodTracingSampling], but 3 should be enough for most cases. We can adjust this
   * in the future if we notice that traces are being truncated in some applications.
   */
  private static final int BUFFER_SIZE_BYTES = 3_000_000;

  private static final int PROFILING_TIMEOUT_MILLIS = 30_000;
  private long transactionStartNanos = 0;
  private long profileStartCpuMillis = 0;
  private @NotNull File traceFilesDir;
  private int intervalUs;
  private @Nullable Future<?> scheduledFinish = null;
  private @Nullable File traceFile = null;
  private @Nullable String frameMetricsCollectorId;
  private volatile @Nullable ProfileEndData timedOutProfilingData = null;
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;  private final @NotNull ArrayDeque<ProfileMeasurementValue> screenFrameRateMeasurements =
    new ArrayDeque<>();
  private final @NotNull ArrayDeque<ProfileMeasurementValue> slowFrameRenderMeasurements =
    new ArrayDeque<>();
  private final @NotNull ArrayDeque<ProfileMeasurementValue> frozenFrameRenderMeasurements =
    new ArrayDeque<>();
  private final @NotNull Map<String, ProfileMeasurement> measurementsMap = new HashMap<>();
  private final @NotNull SentryAndroidOptions options;
  private final @NotNull BuildInfoProvider buildInfoProvider;


  public AndroidProfiler(
    final @NotNull String tracesFilesDirPath,
    final int intervalUs,
    final @NotNull SentryFrameMetricsCollector frameMetricsCollector,
    final @NotNull SentryAndroidOptions sentryAndroidOptions,
    final @NotNull BuildInfoProvider buildInfoProvider
  ) {
    this.traceFilesDir = new File(tracesFilesDirPath);
    this.intervalUs = intervalUs;
    this.frameMetricsCollector = frameMetricsCollector;
    this.options = sentryAndroidOptions;
    this.buildInfoProvider = buildInfoProvider;
  }

  @SuppressLint("NewApi")
  public @Nullable ProfileStartData start() {
    // intervalUs is 0 only if there was a problem in the init, but
    // we already logged that
    if (intervalUs == 0) {
      return null;
    }

    // We create a file with a uuid name, so no need to check if it already exists
    traceFile = new File(traceFilesDir, UUID.randomUUID() + ".trace");

    measurementsMap.clear();
    screenFrameRateMeasurements.clear();
    slowFrameRenderMeasurements.clear();
    frozenFrameRenderMeasurements.clear();

    frameMetricsCollectorId =
      frameMetricsCollector.startCollection(
        new SentryFrameMetricsCollector.FrameMetricsCollectorListener() {
          final long nanosInSecond = TimeUnit.SECONDS.toNanos(1);
          final long frozenFrameThresholdNanos = TimeUnit.MILLISECONDS.toNanos(700);
          float lastRefreshRate = 0;

          @Override
          public void onFrameMetricCollected(
            final long frameEndNanos, final long durationNanos, float refreshRate) {
            // transactionStartNanos is calculated through SystemClock.elapsedRealtimeNanos(),
            // but frameEndNanos uses System.nanotime(), so we convert it to get the timestamp
            // relative to transactionStartNanos
            final long frameTimestampRelativeNanos =
              frameEndNanos
                - System.nanoTime()
                + SystemClock.elapsedRealtimeNanos()
                - transactionStartNanos;

            // We don't allow negative relative timestamps.
            // So we add a check, even if this should never happen.
            if (frameTimestampRelativeNanos < 0) {
              return;
            }
            // Most frames take just a few nanoseconds longer than the optimal calculated
            // duration.
            // Therefore we subtract one, because otherwise almost all frames would be slow.
            boolean isSlow = durationNanos > nanosInSecond / (refreshRate - 1);
            float newRefreshRate = (int) (refreshRate * 100) / 100F;
            if (durationNanos > frozenFrameThresholdNanos) {
              frozenFrameRenderMeasurements.addLast(
                new ProfileMeasurementValue(frameTimestampRelativeNanos, durationNanos));
            } else if (isSlow) {
              slowFrameRenderMeasurements.addLast(
                new ProfileMeasurementValue(frameTimestampRelativeNanos, durationNanos));
            }
            if (newRefreshRate != lastRefreshRate) {
              lastRefreshRate = newRefreshRate;
              screenFrameRateMeasurements.addLast(
                new ProfileMeasurementValue(frameTimestampRelativeNanos, newRefreshRate));
            }
          }
        });

    // We stop profiling after a timeout to avoid huge profiles to be sent
    try {
      scheduledFinish =
        options
          .getExecutorService()
          .schedule(
            () -> timedOutProfilingData = endAndCollect(true, null),
            PROFILING_TIMEOUT_MILLIS);
    } catch (RejectedExecutionException e) {
      options
        .getLogger()
        .log(
          SentryLevel.ERROR,
          "Failed to call the executor. Profiling will not be automatically finished. Did you call Sentry.close()?",
          e);
    }

    transactionStartNanos = SystemClock.elapsedRealtimeNanos();
    profileStartCpuMillis = Process.getElapsedCpuTime();

    // We don't make any check on the file existence or writeable state, because we don't want to
    // make file IO in the main thread.
    // We cannot offload the work to the executorService, as if that's very busy, profiles could
    // start/stop with a lot of delay and even cause ANRs.
    try {
      // If there is any problem with the file this method will throw (but it will not throw in
      // tests)
      Debug.startMethodTracingSampling(traceFile.getPath(), BUFFER_SIZE_BYTES, intervalUs);
      return new ProfileStartData(
        transactionStartNanos,
        profileStartCpuMillis
      );
    } catch (Throwable e) {
      endAndCollect(false, null);
      options.getLogger().log(SentryLevel.ERROR, "Unable to start a profile: ", e);
      return null;
    }
  }

  @SuppressLint("NewApi")
  public @Nullable ProfileEndData endAndCollect(
    final boolean isTimeout,
    final @Nullable List<PerformanceCollectionData> performanceCollectionData
  ) {
    // check if profiling timed out
    if (timedOutProfilingData != null) {
      return timedOutProfilingData;
    }

    // onTransactionStart() is only available since Lollipop
    // and SystemClock.elapsedRealtimeNanos() since Jelly Bean
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) return null;

    try {
      // If there is any problem with the file this method could throw, but the start is also
      // wrapped, so this should never happen (except for tests, where this is the only method that
      // throws)
      Debug.stopMethodTracing();
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error while stopping profiling: ", e);
    }
    frameMetricsCollector.stopCollection(frameMetricsCollectorId);

    long transactionEndNanos = SystemClock.elapsedRealtimeNanos();
    long transactionEndCpuMillis = Process.getElapsedCpuTime();

    if (traceFile == null) {
      options.getLogger().log(SentryLevel.ERROR, "Trace file does not exists");
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
    putPerformanceCollectionDataInMeasurements(performanceCollectionData);

    if (scheduledFinish != null) {
      scheduledFinish.cancel(true);
      scheduledFinish = null;
    }

    return new ProfileEndData(
      transactionEndNanos,
      transactionEndCpuMillis,
      traceFile,
      measurementsMap
    );
  }

  public void close() {
    // we cancel any scheduled work
    if (scheduledFinish != null) {
      scheduledFinish.cancel(true);
      scheduledFinish = null;
    }
  }

  @SuppressLint("NewApi")
  private void putPerformanceCollectionDataInMeasurements(
    final @Nullable List<PerformanceCollectionData> performanceCollectionData) {

    // onTransactionStart() is only available since Lollipop
    // and SystemClock.elapsedRealtimeNanos() since Jelly Bean
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) {
      return;
    }

    // This difference is required, since the PerformanceCollectionData timestamps are expressed in
    // terms of System.currentTimeMillis() and measurements timestamps require the nanoseconds since
    // the beginning, expressed with SystemClock.elapsedRealtimeNanos()
    long timestampDiff =
      SystemClock.elapsedRealtimeNanos()
        - transactionStartNanos
        - TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    if (performanceCollectionData != null) {
      final @NotNull ArrayDeque<ProfileMeasurementValue> memoryUsageMeasurements =
        new ArrayDeque<>(performanceCollectionData.size());
      final @NotNull ArrayDeque<ProfileMeasurementValue> nativeMemoryUsageMeasurements =
        new ArrayDeque<>(performanceCollectionData.size());
      final @NotNull ArrayDeque<ProfileMeasurementValue> cpuUsageMeasurements =
        new ArrayDeque<>(performanceCollectionData.size());
      for (PerformanceCollectionData performanceData : performanceCollectionData) {
        CpuCollectionData cpuData = performanceData.getCpuData();
        MemoryCollectionData memoryData = performanceData.getMemoryData();
        if (cpuData != null) {
          cpuUsageMeasurements.add(
            new ProfileMeasurementValue(
              TimeUnit.MILLISECONDS.toNanos(cpuData.getTimestampMillis()) + timestampDiff,
              cpuData.getCpuUsagePercentage()));
        }
        if (memoryData != null && memoryData.getUsedHeapMemory() > -1) {
          memoryUsageMeasurements.add(
            new ProfileMeasurementValue(
              TimeUnit.MILLISECONDS.toNanos(memoryData.getTimestampMillis()) + timestampDiff,
              memoryData.getUsedHeapMemory()));
        }
        if (memoryData != null && memoryData.getUsedNativeMemory() > -1) {
          nativeMemoryUsageMeasurements.add(
            new ProfileMeasurementValue(
              TimeUnit.MILLISECONDS.toNanos(memoryData.getTimestampMillis()) + timestampDiff,
              memoryData.getUsedNativeMemory()));
        }
      }
      if (!cpuUsageMeasurements.isEmpty()) {
        measurementsMap.put(
          ProfileMeasurement.ID_CPU_USAGE,
          new ProfileMeasurement(ProfileMeasurement.UNIT_PERCENT, cpuUsageMeasurements));
      }
      if (!memoryUsageMeasurements.isEmpty()) {
        measurementsMap.put(
          ProfileMeasurement.ID_MEMORY_FOOTPRINT,
          new ProfileMeasurement(ProfileMeasurement.UNIT_BYTES, memoryUsageMeasurements));
      }
      if (!nativeMemoryUsageMeasurements.isEmpty()) {
        measurementsMap.put(
          ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT,
          new ProfileMeasurement(ProfileMeasurement.UNIT_BYTES, nativeMemoryUsageMeasurements));
      }
    }
  }
}
