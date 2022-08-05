package io.sentry.android.core;

import static android.content.Context.ACTIVITY_SERVICE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Debug;
import android.os.SystemClock;
import io.sentry.ITransaction;
import io.sentry.ITransactionProfiler;
import io.sentry.ProfilingTraceData;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.CpuInfoUtils;
import io.sentry.util.Objects;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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
  private volatile @Nullable ITransaction activeTransaction = null;
  private volatile @Nullable ProfilingTraceData timedOutProfilingData = null;
  private final @NotNull Context context;
  private final @NotNull SentryAndroidOptions options;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @Nullable PackageInfo packageInfo;
  private long transactionStartNanos = 0;
  private boolean isInitialized = false;

  public AndroidTransactionProfiler(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions sentryAndroidOptions,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    this.context = Objects.requireNonNull(context, "The application context is required");
    this.options = Objects.requireNonNull(sentryAndroidOptions, "SentryAndroidOptions is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "The BuildInfoProvider is required.");
    this.packageInfo = ContextUtils.getPackageInfo(context, options.getLogger());
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
    long intervalMillis = options.getProfilingTracesIntervalMillis();
    if (intervalMillis <= 0) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Disabling profiling because trace interval is set to %d milliseconds",
              intervalMillis);
      return;
    }
    intervalUs = (int) MILLISECONDS.toMicros(intervalMillis);
    traceFilesDir = new File(tracesFilesDirPath);
  }

  @SuppressLint("NewApi")
  @Override
  public synchronized void onTransactionStart(@NotNull ITransaction transaction) {

    // Debug.startMethodTracingSampling() is only available since Lollipop
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) return;

    // Let's initialize trace folder and profiling interval
    init();

    // traceFilesDir is null or intervalUs is 0 only if there was a problem in the init, but
    // we already logged that
    if (traceFilesDir == null || intervalUs == 0 || !traceFilesDir.exists()) {
      return;
    }

    // If a transaction is currently being profiled, we ignore this call
    if (activeTransaction != null) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Profiling is already active and was started by transaction %s",
              activeTransaction.getSpanContext().getTraceId().toString());
      return;
    }

    traceFile = new File(traceFilesDir, UUID.randomUUID() + ".trace");

    if (traceFile.exists()) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Trace file already exists: %s", traceFile.getPath());
      return;
    }
    activeTransaction = transaction;

    // We stop the trace after 30 seconds, since such a long trace is very probably a trace
    // that will never end due to an error
    scheduledFinish =
        options
            .getExecutorService()
            .schedule(
                () -> timedOutProfilingData = onTransactionFinish(transaction),
                PROFILING_TIMEOUT_MILLIS);

    transactionStartNanos = SystemClock.elapsedRealtimeNanos();
    Debug.startMethodTracingSampling(traceFile.getPath(), BUFFER_SIZE_BYTES, intervalUs);
  }

  @SuppressLint("NewApi")
  @Override
  public synchronized @Nullable ProfilingTraceData onTransactionFinish(
      @NotNull ITransaction transaction) {

    // onTransactionStart() is only available since Lollipop
    // and SystemClock.elapsedRealtimeNanos() since Jelly Bean
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) return null;

    final ITransaction finalActiveTransaction = activeTransaction;
    final ProfilingTraceData profilingData = timedOutProfilingData;

    // Profiling finished, but we check if we cached last profiling data due to a timeout
    if (finalActiveTransaction == null) {
      // If the cached timed out profiling data refers to the transaction that started it we return
      // it back, otherwise we would simply lose it
      if (profilingData != null) {
        // The timed out transaction is finishing
        if (profilingData
            .getTraceId()
            .equals(transaction.getSpanContext().getTraceId().toString())) {
          timedOutProfilingData = null;
          return profilingData;
        } else {
          // Another transaction is finishing before the timed out one
          options
              .getLogger()
              .log(
                  SentryLevel.ERROR,
                  "Profiling data with id %s exists but doesn't match the closing transaction %s",
                  profilingData.getTraceId(),
                  transaction.getSpanContext().getTraceId().toString());
          return null;
        }
      }
      // A transaction is finishing, but profiling didn't start. Maybe it was started by another one
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "Transaction %s finished, but profiling never started for it. Skipping",
              transaction.getSpanContext().getTraceId().toString());
      return null;
    }

    if (finalActiveTransaction != transaction) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Transaction %s finished, but profiling was started by transaction %s. Skipping",
              transaction.getSpanContext().getTraceId().toString(),
              finalActiveTransaction.getSpanContext().getTraceId().toString());
      return null;
    }

    Debug.stopMethodTracing();
    long transactionDurationNanos = SystemClock.elapsedRealtimeNanos() - transactionStartNanos;

    activeTransaction = null;

    if (scheduledFinish != null) {
      scheduledFinish.cancel(true);
      scheduledFinish = null;
    }

    if (traceFile == null) {
      options.getLogger().log(SentryLevel.ERROR, "Trace file does not exists");
      return null;
    }

    String versionName = "";
    String versionCode = "";
    String totalMem = "0";
    ActivityManager.MemoryInfo memInfo = getMemInfo();
    if (packageInfo != null) {
      versionName = ContextUtils.getVersionName(packageInfo);
      versionCode = ContextUtils.getVersionCode(packageInfo);
    }
    if (memInfo != null) {
      totalMem = Long.toString(memInfo.totalMem);
    }
    String[] abis = Build.SUPPORTED_ABIS;

    // cpu max frequencies are read with a lambda because reading files is involved, so it will be
    // done in the background when the trace file is read
    return new ProfilingTraceData(
        traceFile,
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
        versionName,
        versionCode,
        options.getEnvironment());
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

  @TestOnly
  void setTimedOutProfilingData(@Nullable ProfilingTraceData data) {
    this.timedOutProfilingData = data;
  }
}
