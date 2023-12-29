package io.sentry.android.core;

import static android.content.Context.ACTIVITY_SERVICE;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.ITransactionProfiler;
import io.sentry.PerformanceCollectionData;
import io.sentry.ProfilingTraceData;
import io.sentry.ProfilingTransactionData;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.CpuInfoUtils;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class AndroidTransactionProfiler implements ITransactionProfiler {
  private final @NotNull Context context;
  private final @NotNull SentryAndroidOptions options;
  private final @NotNull IHub hub;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private boolean isInitialized = false;
  private int transactionsCounter = 0;
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;
  private @Nullable ProfilingTransactionData currentProfilingTransactionData;

  private @Nullable ITransaction currentTransaction = null;
  private @Nullable AndroidProfiler profiler = null;
  private long transactionStartNanos;
  private long profileStartCpuMillis;

  public AndroidTransactionProfiler(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions sentryAndroidOptions,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    this(
        context,
        sentryAndroidOptions,
        buildInfoProvider,
        Objects.requireNonNull(
            sentryAndroidOptions.getFrameMetricsCollector(),
            "options.getFrameMetricsCollector cannot be null."),
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

    profiler =
        new AndroidProfiler(
            tracesFilesDirPath,
            (int) SECONDS.toMicros(1) / intervalHz,
            frameMetricsCollector,
            options.getExecutorService(),
            options.getLogger(),
            buildInfoProvider);
  }

  @Override
  public synchronized void onTransactionStart(final @NotNull ITransaction transaction) {
    // Debug.startMethodTracingSampling() is only available since Lollipop
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) return;

    // Let's initialize trace folder and profiling interval
    init();

    transactionsCounter++;
    // When the first transaction is starting, we can start profiling
    if (transactionsCounter == 1) {
      if (onFirstTransactionStarted(transaction)) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Transaction %s (%s) started and being profiled.",
                transaction.getName(),
                transaction.getSpanContext().getTraceId().toString());
      }
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
  private boolean onFirstTransactionStarted(final @NotNull ITransaction transaction) {
    // init() didn't create profiler, should never happen
    if (profiler == null) {
      return false;
    }

    final AndroidProfiler.ProfileStartData startData = profiler.start();
    // check if profiling started
    if (startData == null) {
      return false;
    }
    transactionStartNanos = startData.startNanos;
    profileStartCpuMillis = startData.startCpuMillis;

    currentTransaction = transaction;

    currentProfilingTransactionData =
        new ProfilingTransactionData(transaction, transactionStartNanos, profileStartCpuMillis);
    return true;
  }

  @Override
  public @Nullable synchronized ProfilingTraceData onTransactionFinish(
      final @NotNull ITransaction transaction,
      final @Nullable List<PerformanceCollectionData> performanceCollectionData) {

    return onTransactionFinish(transaction, false, performanceCollectionData);
  }

  @SuppressLint("NewApi")
  private @Nullable synchronized ProfilingTraceData onTransactionFinish(
      final @NotNull ITransaction transaction,
      final boolean isTimeout,
      final @Nullable List<PerformanceCollectionData> performanceCollectionData) {
    // check if profiler was created
    if (profiler == null) {
      return null;
    }

    // onTransactionStart() is only available since Lollipop
    // and SystemClock.elapsedRealtimeNanos() since Jelly Bean
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) return null;

    // Transaction finished, but it's not in the current profile
    if (currentProfilingTransactionData == null
        || !currentProfilingTransactionData.getId().equals(transaction.getEventId().toString())) {
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

    if (transactionsCounter != 0) {
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

    final AndroidProfiler.ProfileEndData endData =
        profiler.endAndCollect(false, performanceCollectionData);
    // check if profiler end successfully
    if (endData == null) {
      return null;
    }

    long transactionDurationNanos = endData.endNanos - transactionStartNanos;

    List<ProfilingTransactionData> transactionList = new ArrayList<>(1);
    final ProfilingTransactionData txData = currentProfilingTransactionData;
    if (txData != null) {
      transactionList.add(txData);
    }
    currentProfilingTransactionData = null;
    // We clear the counter in case of a timeout
    transactionsCounter = 0;
    currentTransaction = null;

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
          endData.endNanos, transactionStartNanos, endData.endCpuMillis, profileStartCpuMillis);
    }

    // cpu max frequencies are read with a lambda because reading files is involved, so it will be
    // done in the background when the trace file is read
    return new ProfilingTraceData(
        endData.traceFile,
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
        (endData.didTimeout || isTimeout)
            ? ProfilingTraceData.TRUNCATION_REASON_TIMEOUT
            : ProfilingTraceData.TRUNCATION_REASON_NORMAL,
        endData.measurementsMap);
  }

  @Override
  public void close() {
    // we stop profiling
    if (currentTransaction != null) {
      onTransactionFinish(currentTransaction, true, null);
    }

    // we have to first stop profiling otherwise we would lost the last profile
    if (profiler != null) {
      profiler.close();
    }
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
  @Nullable
  ITransaction getCurrentTransaction() {
    return currentTransaction;
  }
}
