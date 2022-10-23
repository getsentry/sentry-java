package io.sentry.android.core;

import static android.content.Context.ACTIVITY_SERVICE;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.ITransactionProfiler;
import io.sentry.ProfilingTraceData;
import io.sentry.ProfilingTransactionData;
import io.sentry.SentryEnvelope;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.CpuInfoUtils;
import io.sentry.exception.SentryEnvelopeException;
import io.sentry.util.Objects;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
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
  private final @NotNull Context context;
  private final @NotNull SentryAndroidOptions options;
  private final @NotNull IHub hub;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @Nullable PackageInfo packageInfo;
  private long transactionStartNanos = 0;
  private long profileStartCpuMillis = 0;
  private boolean isInitialized = false;
  private int transactionsCounter = 0;
  private final @NotNull Map<String, ProfilingTransactionData> transactionMap = new HashMap<>();

  public AndroidTransactionProfiler(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions sentryAndroidOptions,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    this(context, sentryAndroidOptions, buildInfoProvider, HubAdapter.getInstance());
  }

  public AndroidTransactionProfiler(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions sentryAndroidOptions,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull IHub hub) {
    this.context = Objects.requireNonNull(context, "The application context is required");
    this.options = Objects.requireNonNull(sentryAndroidOptions, "SentryAndroidOptions is required");
    this.hub = Objects.requireNonNull(hub, "Hub is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "The BuildInfoProvider is required.");
    this.packageInfo = ContextUtils.getPackageInfo(context, options.getLogger(), buildInfoProvider);
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

    transactionsCounter++;
    // When the first transaction is starting, we can start profiling
    if (transactionsCounter == 1) {

      traceFile = new File(traceFilesDir, UUID.randomUUID() + ".trace");

      if (traceFile.exists()) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Trace file already exists: %s", traceFile.getPath());
        transactionsCounter--;
        return;
      }

      // We stop profiling after a timeout to avoid huge profiles to be sent
      scheduledFinish =
          options
              .getExecutorService()
              .schedule(() -> onTransactionFinish(transaction, true), PROFILING_TIMEOUT_MILLIS);

      transactionStartNanos = SystemClock.elapsedRealtimeNanos();
      profileStartCpuMillis = Process.getElapsedCpuTime();

      ProfilingTransactionData transactionData =
          new ProfilingTransactionData(transaction, transactionStartNanos, profileStartCpuMillis);
      transactionMap.put(transaction.getEventId().toString(), transactionData);

      Debug.startMethodTracingSampling(traceFile.getPath(), BUFFER_SIZE_BYTES, intervalUs);
    } else {
      ProfilingTransactionData transactionData =
          new ProfilingTransactionData(
              transaction, SystemClock.elapsedRealtimeNanos(), Process.getElapsedCpuTime());
      transactionMap.put(transaction.getEventId().toString(), transactionData);
    }
    options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "Transaction %s (%s) started. Transactions being profiled: %d",
            transaction.getName(),
            transaction.getSpanContext().getTraceId().toString(),
            transactionsCounter);
  }

  @Override
  public synchronized void onTransactionFinish(@NotNull ITransaction transaction) {
    onTransactionFinish(transaction, false);
  }

  @SuppressLint("NewApi")
  private synchronized void onTransactionFinish(
      @NotNull ITransaction transaction, boolean isTimeout) {

    // onTransactionStart() is only available since Lollipop
    // and SystemClock.elapsedRealtimeNanos() since Jelly Bean
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) return;

    // Transaction finished, but it's not in the current profile. We can skip it
    if (!transactionMap.containsKey(transaction.getEventId().toString())) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "Transaction %s (%s) finished, but was not currently being profiled. Skipping",
              transaction.getName(),
              transaction.getSpanContext().getTraceId().toString());
      return;
    }

    if (transactionsCounter > 0) {
      transactionsCounter--;
    }

    options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "Transaction %s (%s) finished. Transactions to be profiled: %d",
            transaction.getName(),
            transaction.getSpanContext().getTraceId().toString(),
            transactionsCounter);

    if (transactionsCounter != 0 && !isTimeout) {
      // We notify the data referring to this transaction that it finished
      ProfilingTransactionData transactionData =
          transactionMap.get(transaction.getEventId().toString());
      if (transactionData != null) {
        transactionData.notifyFinish(
            SystemClock.elapsedRealtimeNanos(),
            transactionStartNanos,
            Process.getElapsedCpuTime(),
            profileStartCpuMillis);
      }
      return;
    }

    Debug.stopMethodTracing();
    long transactionEndNanos = SystemClock.elapsedRealtimeNanos();
    long transactionEndCpuMillis = Process.getElapsedCpuTime();
    long transactionDurationNanos = transactionEndNanos - transactionStartNanos;

    List<ProfilingTransactionData> transactionList = new ArrayList<>(transactionMap.values());
    transactionMap.clear();
    // We clear the counter in case of a timeout
    transactionsCounter = 0;

    if (scheduledFinish != null) {
      scheduledFinish.cancel(true);
      scheduledFinish = null;
    }

    if (traceFile == null) {
      options.getLogger().log(SentryLevel.ERROR, "Trace file does not exists");
      return;
    }

    String versionName = "";
    String versionCode = "";
    String totalMem = "0";
    ActivityManager.MemoryInfo memInfo = getMemInfo();
    if (packageInfo != null) {
      versionName = ContextUtils.getVersionName(packageInfo);
      versionCode = ContextUtils.getVersionCode(packageInfo, buildInfoProvider);
    }
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

    // cpu max frequencies are read with a lambda because reading files is involved, so it will be
    // done in the background when the trace file is read
    ProfilingTraceData profilingTraceData =
        new ProfilingTraceData(
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
            versionName,
            versionCode,
            options.getEnvironment(),
            isTimeout
                ? ProfilingTraceData.TRUNCATION_REASON_TIMEOUT
                : ProfilingTraceData.TRUNCATION_REASON_NORMAL);

    SentryEnvelope envelope;
    try {
      envelope =
          SentryEnvelope.from(
              options.getSerializer(),
              profilingTraceData,
              options.getMaxTraceFileSize(),
              options.getSdkVersion());
    } catch (SentryEnvelopeException e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to capture profile.", e);
      return;
    }

    hub.captureEnvelope(envelope);
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
