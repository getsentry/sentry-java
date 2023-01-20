package io.sentry.android.core;

import static android.content.Context.ACTIVITY_SERVICE;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.view.FrameMetrics;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.ITransactionProfiler;
import io.sentry.ProfilingTraceData;
import io.sentry.ProfilingTransactionData;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.CpuInfoUtils;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.profilemeasurements.ProfileMeasurement;
import io.sentry.profilemeasurements.ProfileMeasurementValue;
import io.sentry.util.Objects;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AndroidTransactionProfiler implements ITransactionProfiler {

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

  private int intervalUs;
  private @Nullable File traceFile = null;
  private @Nullable File traceFilesDir = null;
  private @Nullable Future<?> scheduledFinish = null;
  private volatile @Nullable ProfilingTraceData timedOutProfilingData = null;
  private final @NotNull Context context;
  private final @NotNull SentryAndroidOptions options;
  private final @NotNull IHub hub;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private long transactionStartNanos = 0;
  private long profileStartCpuMillis = 0;
  private boolean isInitialized = false;
  private int transactionsCounter = 0;
  private @Nullable String frameMetricsCollectorId;
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;
  private @Nullable ProfilingTransactionData currentProfilingTransactionData;
  private final @NotNull ArrayDeque<ProfileMeasurementValue> screenFrameRateMeasurements =
      new ArrayDeque<>();
  private final @NotNull ArrayDeque<ProfileMeasurementValue> slowFrameRenderMeasurements =
      new ArrayDeque<>();
  private final @NotNull ArrayDeque<ProfileMeasurementValue> frozenFrameRenderMeasurements =
      new ArrayDeque<>();
  private final @NotNull Map<String, ProfileMeasurement> measurementsMap = new HashMap<>();

  public AndroidTransactionProfiler(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions sentryAndroidOptions,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector) {
    this(
        context,
        sentryAndroidOptions,
        buildInfoProvider,
        frameMetricsCollector,
        HubAdapter.getInstance());
  }

  public AndroidTransactionProfiler(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions sentryAndroidOptions,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector,
      final @NotNull IHub hub) {
    this.context = Objects.requireNonNull(context, "The application context is required");
    this.options = Objects.requireNonNull(sentryAndroidOptions, "SentryAndroidOptions is required");
    this.hub = Objects.requireNonNull(hub, "Hub is required");
    this.frameMetricsCollector =
        Objects.requireNonNull(frameMetricsCollector, "SentryFrameMetricsCollector is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "The BuildInfoProvider is required.");
  }

  private void init() {
    // We initialize it only once
    if (isInitialized) {
      return;
    }
    isInitialized = true;
    final String tracesFilesDirPath = options.getProfilingTracesDirPath();
    if (!options.isProfilingEnabled()) {
      options.getLogger().log(SentryLevel.INFO, "Profiling is disabled in options.");
      return;
    }
    if (tracesFilesDirPath == null) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Disabling profiling because no profiling traces dir path is defined in options.");
      return;
    }
    final int intervalHz = options.getProfilingTracesHz();
    if (intervalHz <= 0) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Disabling profiling because trace rate is set to %d",
              intervalHz);
      return;
    }
    intervalUs = (int) SECONDS.toMicros(1) / intervalHz;
    traceFilesDir = new File(tracesFilesDirPath);
  }

  @Override
  public synchronized void onTransactionStart(final @NotNull ITransaction transaction) {
    options.getExecutorService().submit(() -> onTransactionStartSafe(transaction));
  }

  @SuppressLint("NewApi")
  private void onTransactionStartSafe(final @NotNull ITransaction transaction) {

    // Debug.startMethodTracingSampling() is only available since Lollipop
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) return;

    // Let's initialize trace folder and profiling interval
    init();

    // traceFilesDir is null or intervalUs is 0 only if there was a problem in the init, but
    // we already logged that
    if (traceFilesDir == null || intervalUs == 0 || !traceFilesDir.canWrite()) {
      return;
    }

    transactionsCounter++;
    // When the first transaction is starting, we can start profiling
    if (transactionsCounter == 1) {
      onFirstTransactionStarted(transaction);
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Transaction %s (%s) started and being profiled.",
              transaction.getName(),
              transaction.getSpanContext().getTraceId().toString());
    } else {
      transactionsCounter--;
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "A transaction is already being profiled. Transaction %s (%s) will be ignored.",
              transaction.getName(),
              transaction.getSpanContext().getTraceId().toString());
    }
  }

  @SuppressLint("NewApi")
  private void onFirstTransactionStarted(final @NotNull ITransaction transaction) {
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
                  @NotNull FrameMetrics frameMetrics, float refreshRate) {
                long frameTimestampRelativeNanos =
                    SystemClock.elapsedRealtimeNanos() - transactionStartNanos;

                // We don't allow negative relative timestamps.
                // So we add a check, even if this should never happen.
                if (frameTimestampRelativeNanos < 0) {
                  return;
                }
                long durationNanos = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION);
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
    scheduledFinish =
        options
            .getExecutorService()
            .schedule(
                () -> timedOutProfilingData = onTransactionFinish(transaction, true),
                PROFILING_TIMEOUT_MILLIS);

    transactionStartNanos = SystemClock.elapsedRealtimeNanos();
    profileStartCpuMillis = Process.getElapsedCpuTime();

    currentProfilingTransactionData =
        new ProfilingTransactionData(transaction, transactionStartNanos, profileStartCpuMillis);

    Debug.startMethodTracingSampling(traceFile.getPath(), BUFFER_SIZE_BYTES, intervalUs);
  }

  @Override
  public @Nullable synchronized ProfilingTraceData onTransactionFinish(
      final @NotNull ITransaction transaction) {
    try {
      return options
          .getExecutorService()
          .submit(() -> onTransactionFinish(transaction, false))
          .get();
    } catch (ExecutionException e) {
      options.getLogger().log(SentryLevel.ERROR, "Error finishing profiling: ", e);
    } catch (InterruptedException e) {
      options.getLogger().log(SentryLevel.ERROR, "Error finishing profiling: ", e);
    }
    return null;
  }

  @SuppressLint("NewApi")
  private @Nullable ProfilingTraceData onTransactionFinish(
      final @NotNull ITransaction transaction, final boolean isTimeout) {

    // onTransactionStart() is only available since Lollipop
    // and SystemClock.elapsedRealtimeNanos() since Jelly Bean
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) return null;

    final ProfilingTraceData profilingData = timedOutProfilingData;

    // Transaction finished, but it's not in the current profile
    if (currentProfilingTransactionData == null
        || !currentProfilingTransactionData.getId().equals(transaction.getEventId().toString())) {
      // We check if we cached a profiling data due to a timeout with this profile in it
      // If so, we return it back, otherwise we would simply lose it
      if (profilingData != null) {
        if (profilingData.getTransactionId().equals(transaction.getEventId().toString())) {
          timedOutProfilingData = null;
          return profilingData;
        } else {
          // Another transaction is finishing before the timed out one
          options
              .getLogger()
              .log(
                  SentryLevel.INFO,
                  "A timed out profiling data exists, but the finishing transaction %s (%s) is not part of it",
                  transaction.getName(),
                  transaction.getSpanContext().getTraceId().toString());
          return null;
        }
      }
      // A transaction is finishing, but it's not profiled. We can skip it
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "Transaction %s (%s) finished, but was not currently being profiled. Skipping",
              transaction.getName(),
              transaction.getSpanContext().getTraceId().toString());
      return null;
    }

    if (transactionsCounter > 0) {
      transactionsCounter--;
    }

    options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "Transaction %s (%s) finished.",
            transaction.getName(),
            transaction.getSpanContext().getTraceId().toString());

    if (transactionsCounter != 0 && !isTimeout) {
      // We notify the data referring to this transaction that it finished
      if (currentProfilingTransactionData != null) {
        currentProfilingTransactionData.notifyFinish(
            SystemClock.elapsedRealtimeNanos(),
            transactionStartNanos,
            Process.getElapsedCpuTime(),
            profileStartCpuMillis);
      }
      return null;
    }

    Debug.stopMethodTracing();
    frameMetricsCollector.stopCollection(frameMetricsCollectorId);

    long transactionEndNanos = SystemClock.elapsedRealtimeNanos();
    long transactionEndCpuMillis = Process.getElapsedCpuTime();
    long transactionDurationNanos = transactionEndNanos - transactionStartNanos;

    List<ProfilingTransactionData> transactionList = new ArrayList<>(1);
    transactionList.add(currentProfilingTransactionData);
    currentProfilingTransactionData = null;
    // We clear the counter in case of a timeout
    transactionsCounter = 0;

    if (scheduledFinish != null) {
      scheduledFinish.cancel(true);
      scheduledFinish = null;
    }

    if (traceFile == null) {
      options.getLogger().log(SentryLevel.ERROR, "Trace file does not exists");
      return null;
    }

    String totalMem = "0";
    ActivityManager.MemoryInfo memInfo = getMemInfo();
    if (memInfo != null) {
      totalMem = Long.toString(memInfo.totalMem);
    }
    String[] abis = Build.SUPPORTED_ABIS;

    // We notify all transactions data that all transactions finished.
    // Some may not have been really finished, in case of a timeout
    for (ProfilingTransactionData t : transactionList) {
      t.notifyFinish(
          transactionEndNanos,
          transactionStartNanos,
          transactionEndCpuMillis,
          profileStartCpuMillis);
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

    // cpu max frequencies are read with a lambda because reading files is involved, so it will be
    // done in the background when the trace file is read
    return new ProfilingTraceData(
        traceFile,
        transactionList,
        transaction,
        Long.toString(transactionDurationNanos),
        buildInfoProvider.getSdkInfoVersion(),
        abis != null && abis.length > 0 ? abis[0] : "",
        () -> CpuInfoUtils.getInstance().readMaxFrequencies(),
        buildInfoProvider.getManufacturer(),
        buildInfoProvider.getModel(),
        buildInfoProvider.getVersionRelease(),
        buildInfoProvider.isEmulator(),
        totalMem,
        options.getProguardUuid(),
        options.getRelease(),
        options.getEnvironment(),
        isTimeout
            ? ProfilingTraceData.TRUNCATION_REASON_TIMEOUT
            : ProfilingTraceData.TRUNCATION_REASON_NORMAL,
        measurementsMap);
  }

  /**
   * Get MemoryInfo object representing the memory state of the application.
   *
   * @return MemoryInfo object representing the memory state of the application
   */
  private @Nullable ActivityManager.MemoryInfo getMemInfo() {
    try {
      ActivityManager actManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
      ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
      if (actManager != null) {
        actManager.getMemoryInfo(memInfo);
        return memInfo;
      }
      options.getLogger().log(SentryLevel.INFO, "Error getting MemoryInfo.");
      return null;
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting MemoryInfo.", e);
      return null;
    }
  }
}
