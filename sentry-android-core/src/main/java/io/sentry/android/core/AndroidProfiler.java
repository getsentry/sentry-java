package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import io.sentry.CpuCollectionData;
import io.sentry.DateUtils;
import io.sentry.ILogger;
import io.sentry.ISentryExecutorService;
import io.sentry.ISentryLifecycleToken;
import io.sentry.MemoryCollectionData;
import io.sentry.PerformanceCollectionData;
import io.sentry.SentryLevel;
import io.sentry.SentryUUID;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.profilemeasurements.ProfileMeasurement;
import io.sentry.profilemeasurements.ProfileMeasurementValue;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class AndroidProfiler {
  public static class ProfileStartData {
    public final long startNanos;
    public final long startCpuMillis;
    public final @NotNull Date startTimestamp;

    public ProfileStartData(
        final long startNanos, final long startCpuMillis, final @NotNull Date startTimestamp) {
      this.startNanos = startNanos;
      this.startCpuMillis = startCpuMillis;
      this.startTimestamp = startTimestamp;
    }
  }

  public static class ProfileEndData {
    public final long endNanos;
    public final long endCpuMillis;
    public final @NotNull File traceFile;
    public final @NotNull Map<String, ProfileMeasurement> measurementsMap;
    public final boolean didTimeout;

    public ProfileEndData(
        final long endNanos,
        final long endCpuMillis,
        final boolean didTimeout,
        final @NotNull File traceFile,
        final @NotNull Map<String, ProfileMeasurement> measurementsMap) {
      this.endNanos = endNanos;
      this.traceFile = traceFile;
      this.endCpuMillis = endCpuMillis;
      this.measurementsMap = measurementsMap;
      this.didTimeout = didTimeout;
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
  private long profileStartNanos = 0;
  private final @NotNull File traceFilesDir;
  private final int intervalUs;
  private @Nullable Future<?> scheduledFinish = null;
  private @Nullable File traceFile = null;
  private @Nullable String frameMetricsCollectorId;
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;
  private final @NotNull ArrayDeque<ProfileMeasurementValue> screenFrameRateMeasurements =
      new ArrayDeque<>();
  private final @NotNull ArrayDeque<ProfileMeasurementValue> slowFrameRenderMeasurements =
      new ArrayDeque<>();
  private final @NotNull ArrayDeque<ProfileMeasurementValue> frozenFrameRenderMeasurements =
      new ArrayDeque<>();
  private final @NotNull Map<String, ProfileMeasurement> measurementsMap = new HashMap<>();
  private final @NotNull ISentryExecutorService executorService;
  private final @NotNull ILogger logger;
  private boolean isRunning = false;
  protected final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public AndroidProfiler(
      final @NotNull String tracesFilesDirPath,
      final int intervalUs,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector,
      final @NotNull ISentryExecutorService executorService,
      final @NotNull ILogger logger) {
    this.traceFilesDir =
        new File(Objects.requireNonNull(tracesFilesDirPath, "TracesFilesDirPath is required"));
    this.intervalUs = intervalUs;
    this.logger = Objects.requireNonNull(logger, "Logger is required");
    this.executorService = Objects.requireNonNull(executorService, "ExecutorService is required.");
    this.frameMetricsCollector =
        Objects.requireNonNull(frameMetricsCollector, "SentryFrameMetricsCollector is required");
  }

  @SuppressLint("NewApi")
  public @Nullable ProfileStartData start() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      // intervalUs is 0 only if there was a problem in the init
      if (intervalUs == 0) {
        logger.log(
            SentryLevel.WARNING, "Disabling profiling because intervaUs is set to %d", intervalUs);
        return null;
      }

      if (isRunning) {
        logger.log(SentryLevel.WARNING, "Profiling has already started...");
        return null;
      }

      // We create a file with a uuid name, so no need to check if it already exists
      traceFile = new File(traceFilesDir, SentryUUID.generateSentryId() + ".trace");

      measurementsMap.clear();
      screenFrameRateMeasurements.clear();
      slowFrameRenderMeasurements.clear();
      frozenFrameRenderMeasurements.clear();

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
                  // profileStartNanos is calculated through SystemClock.elapsedRealtimeNanos(),
                  // but frameEndNanos uses System.nanotime(), so we convert it to get the timestamp
                  // relative to profileStartNanos
                  final long frameTimestampRelativeNanos =
                      frameEndNanos
                          - System.nanoTime()
                          + SystemClock.elapsedRealtimeNanos()
                          - profileStartNanos;

                  // We don't allow negative relative timestamps.
                  // So we add a check, even if this should never happen.
                  if (frameTimestampRelativeNanos < 0) {
                    return;
                  }
                  if (isFrozen) {
                    frozenFrameRenderMeasurements.addLast(
                        new ProfileMeasurementValue(frameTimestampRelativeNanos, durationNanos));
                  } else if (isSlow) {
                    slowFrameRenderMeasurements.addLast(
                        new ProfileMeasurementValue(frameTimestampRelativeNanos, durationNanos));
                  }
                  if (refreshRate != lastRefreshRate) {
                    lastRefreshRate = refreshRate;
                    screenFrameRateMeasurements.addLast(
                        new ProfileMeasurementValue(frameTimestampRelativeNanos, refreshRate));
                  }
                }
              });

      // We stop profiling after a timeout to avoid huge profiles to be sent
      try {
        scheduledFinish =
            executorService.schedule(() -> endAndCollect(true, null), PROFILING_TIMEOUT_MILLIS);
      } catch (RejectedExecutionException e) {
        logger.log(
            SentryLevel.ERROR,
            "Failed to call the executor. Profiling will not be automatically finished. Did you call Sentry.close()?",
            e);
      }

      profileStartNanos = SystemClock.elapsedRealtimeNanos();
      final @NotNull Date profileStartTimestamp = DateUtils.getCurrentDateTime();
      long profileStartCpuMillis = Process.getElapsedCpuTime();

      // We don't make any check on the file existence or writeable state, because we don't want to
      // make file IO in the main thread.
      // We cannot offload the work to the executorService, as if that's very busy, profiles could
      // start/stop with a lot of delay and even cause ANRs.
      try {
        // If there is any problem with the file this method will throw (but it will not throw in
        // tests)
        Debug.startMethodTracingSampling(traceFile.getPath(), BUFFER_SIZE_BYTES, intervalUs);
        isRunning = true;
        return new ProfileStartData(
            profileStartNanos, profileStartCpuMillis, profileStartTimestamp);
      } catch (Throwable e) {
        endAndCollect(false, null);
        logger.log(SentryLevel.ERROR, "Unable to start a profile: ", e);
        isRunning = false;
        return null;
      }
    }
  }

  @SuppressLint("NewApi")
  public @Nullable ProfileEndData endAndCollect(
      final boolean isTimeout,
      final @Nullable List<PerformanceCollectionData> performanceCollectionData) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (!isRunning) {
        logger.log(SentryLevel.WARNING, "Profiler not running");
        return null;
      }

      try {
        // If there is any problem with the file this method could throw, but the start is also
        // wrapped, so this should never happen (except for tests, where this is the only method
        // that
        // throws)
        Debug.stopMethodTracing();
      } catch (Throwable e) {
        logger.log(SentryLevel.ERROR, "Error while stopping profiling: ", e);
      } finally {
        isRunning = false;
      }
      frameMetricsCollector.stopCollection(frameMetricsCollectorId);

      long transactionEndNanos = SystemClock.elapsedRealtimeNanos();
      long transactionEndCpuMillis = Process.getElapsedCpuTime();

      if (traceFile == null) {
        logger.log(SentryLevel.ERROR, "Trace file does not exists");
        return null;
      }

      if (!slowFrameRenderMeasurements.isEmpty()) {
        measurementsMap.put(
            ProfileMeasurement.ID_SLOW_FRAME_RENDERS,
            new ProfileMeasurement(
                ProfileMeasurement.UNIT_NANOSECONDS, slowFrameRenderMeasurements));
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
          transactionEndNanos, transactionEndCpuMillis, isTimeout, traceFile, measurementsMap);
    }
  }

  public void close() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      // we cancel any scheduled work
      if (scheduledFinish != null) {
        scheduledFinish.cancel(true);
        scheduledFinish = null;
      }

      // stop profiling if running
      if (isRunning) {
        endAndCollect(true, null);
      }
    }
  }

  @SuppressLint("NewApi")
  private void putPerformanceCollectionDataInMeasurements(
      final @Nullable List<PerformanceCollectionData> performanceCollectionData) {

    // This difference is required, since the PerformanceCollectionData timestamps are expressed in
    // terms of System.currentTimeMillis() and measurements timestamps require the nanoseconds since
    // the beginning, expressed with SystemClock.elapsedRealtimeNanos()
    long timestampDiff =
        SystemClock.elapsedRealtimeNanos()
            - profileStartNanos
            - TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    if (performanceCollectionData != null) {
      final @NotNull ArrayDeque<ProfileMeasurementValue> memoryUsageMeasurements =
          new ArrayDeque<>(performanceCollectionData.size());
      final @NotNull ArrayDeque<ProfileMeasurementValue> nativeMemoryUsageMeasurements =
          new ArrayDeque<>(performanceCollectionData.size());
      final @NotNull ArrayDeque<ProfileMeasurementValue> cpuUsageMeasurements =
          new ArrayDeque<>(performanceCollectionData.size());

      synchronized (performanceCollectionData) {
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
