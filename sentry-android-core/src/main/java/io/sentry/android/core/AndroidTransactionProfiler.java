package io.sentry.android.core;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import io.sentry.DateUtils;
import io.sentry.ILogger;
import io.sentry.ISentryExecutorService;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ITransaction;
import io.sentry.ITransactionProfiler;
import io.sentry.PerformanceCollectionData;
import io.sentry.ProfilingTraceData;
import io.sentry.ProfilingTransactionData;
import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.util.CpuInfoUtils;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class AndroidTransactionProfiler implements ITransactionProfiler {
  private final @NotNull Context context;
  private final @NotNull ILogger logger;
  private final @Nullable String profilingTracesDirPath;
  private final boolean isProfilingEnabled;
  private final int profilingTracesHz;
  private final @NotNull ISentryExecutorService executorService;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private boolean isInitialized = false;
  private int transactionsCounter = 0;
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;
  private @Nullable ProfilingTransactionData currentProfilingTransactionData;
  private @Nullable AndroidProfiler profiler = null;
  private long profileStartNanos;
  private long profileStartCpuMillis;
  private @NotNull Date profileStartTimestamp;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public AndroidTransactionProfiler(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions sentryAndroidOptions,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector) {
    this(
        context,
        buildInfoProvider,
        frameMetricsCollector,
        sentryAndroidOptions.getLogger(),
        sentryAndroidOptions.getProfilingTracesDirPath(),
        sentryAndroidOptions.isProfilingEnabled(),
        sentryAndroidOptions.getProfilingTracesHz(),
        sentryAndroidOptions.getExecutorService());
  }

  public AndroidTransactionProfiler(
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector,
      final @NotNull ILogger logger,
      final @Nullable String profilingTracesDirPath,
      final boolean isProfilingEnabled,
      final int profilingTracesHz,
      final @NotNull ISentryExecutorService executorService) {
    this.context =
        Objects.requireNonNull(
            ContextUtils.getApplicationContext(context), "The application context is required");
    this.logger = Objects.requireNonNull(logger, "ILogger is required");
    this.frameMetricsCollector =
        Objects.requireNonNull(frameMetricsCollector, "SentryFrameMetricsCollector is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "The BuildInfoProvider is required.");
    this.profilingTracesDirPath = profilingTracesDirPath;
    this.isProfilingEnabled = isProfilingEnabled;
    this.profilingTracesHz = profilingTracesHz;
    this.executorService =
        Objects.requireNonNull(executorService, "The ISentryExecutorService is required.");
    this.profileStartTimestamp = DateUtils.getCurrentDateTime();
  }

  private void init() {
    // We initialize it only once
    if (isInitialized) {
      return;
    }
    isInitialized = true;
    if (!isProfilingEnabled) {
      logger.log(SentryLevel.INFO, "Profiling is disabled in options.");
      return;
    }
    if (profilingTracesDirPath == null) {
      logger.log(
          SentryLevel.WARNING,
          "Disabling profiling because no profiling traces dir path is defined in options.");
      return;
    }
    if (profilingTracesHz <= 0) {
      logger.log(
          SentryLevel.WARNING,
          "Disabling profiling because trace rate is set to %d",
          profilingTracesHz);
      return;
    }

    profiler =
        new AndroidProfiler(
            profilingTracesDirPath,
            (int) SECONDS.toMicros(1) / profilingTracesHz,
            frameMetricsCollector,
            executorService,
            logger);
  }

  @Override
  public void start() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      // Debug.startMethodTracingSampling() is only available since Lollipop, but Android Profiler
      // causes crashes on api 21 -> https://github.com/getsentry/sentry-java/issues/3392
      if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP_MR1) return;

      // Let's initialize trace folder and profiling interval
      init();

      transactionsCounter++;
      // When the first transaction is starting, we can start profiling
      if (transactionsCounter == 1 && onFirstStart()) {
        logger.log(SentryLevel.DEBUG, "Profiler started.");
      } else {
        transactionsCounter--;
        logger.log(
            SentryLevel.WARNING, "A profile is already running. This profile will be ignored.");
      }
    }
  }

  @SuppressLint("NewApi")
  private boolean onFirstStart() {
    // init() didn't create profiler, should never happen
    if (profiler == null) {
      return false;
    }

    final AndroidProfiler.ProfileStartData startData = profiler.start();
    // check if profiling started
    if (startData == null) {
      return false;
    }
    profileStartNanos = startData.startNanos;
    profileStartCpuMillis = startData.startCpuMillis;
    profileStartTimestamp = startData.startTimestamp;
    return true;
  }

  @Override
  public void bindTransaction(final @NotNull ITransaction transaction) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      // If the profiler is running, but no profilingTransactionData is set, we bind it here
      if (transactionsCounter > 0 && currentProfilingTransactionData == null) {
        currentProfilingTransactionData =
            new ProfilingTransactionData(transaction, profileStartNanos, profileStartCpuMillis);
      }
    }
  }

  @Override
  public @Nullable ProfilingTraceData onTransactionFinish(
      final @NotNull ITransaction transaction,
      final @Nullable List<PerformanceCollectionData> performanceCollectionData,
      final @NotNull SentryOptions options) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return onTransactionFinish(
          transaction.getName(),
          transaction.getEventId().toString(),
          transaction.getSpanContext().getTraceId().toString(),
          false,
          performanceCollectionData,
          options);
    }
  }

  @SuppressLint("NewApi")
  private @Nullable ProfilingTraceData onTransactionFinish(
      final @NotNull String transactionName,
      final @NotNull String transactionId,
      final @NotNull String traceId,
      final boolean isTimeout,
      final @Nullable List<PerformanceCollectionData> performanceCollectionData,
      final @NotNull SentryOptions options) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      // check if profiler was created
      if (profiler == null) {
        return null;
      }

      // onTransactionStart() is only available since Lollipop_MR1
      // and SystemClock.elapsedRealtimeNanos() since Jelly Bean
      if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP_MR1) return null;

      // Transaction finished, but it's not in the current profile
      if (currentProfilingTransactionData == null
          || !currentProfilingTransactionData.getId().equals(transactionId)) {
        // A transaction is finishing, but it's not profiled. We can skip it
        logger.log(
            SentryLevel.INFO,
            "Transaction %s (%s) finished, but was not currently being profiled. Skipping",
            transactionName,
            traceId);
        return null;
      }

      if (transactionsCounter > 0) {
        transactionsCounter--;
      }

      logger.log(SentryLevel.DEBUG, "Transaction %s (%s) finished.", transactionName, traceId);

      if (transactionsCounter != 0) {
        // We notify the data referring to this transaction that it finished
        if (currentProfilingTransactionData != null) {
          currentProfilingTransactionData.notifyFinish(
              SystemClock.elapsedRealtimeNanos(),
              profileStartNanos,
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

      long transactionDurationNanos = endData.endNanos - profileStartNanos;

      List<ProfilingTransactionData> transactionList = new ArrayList<>(1);
      final ProfilingTransactionData txData = currentProfilingTransactionData;
      if (txData != null) {
        transactionList.add(txData);
      }
      currentProfilingTransactionData = null;
      // We clear the counter in case of a timeout
      transactionsCounter = 0;

      String totalMem = "0";
      final @Nullable Long memory =
          (options instanceof SentryAndroidOptions)
              ? DeviceInfoUtil.getInstance(context, (SentryAndroidOptions) options).getTotalMemory()
              : null;
      if (memory != null) {
        totalMem = Long.toString(memory);
      }
      String[] abis = Build.SUPPORTED_ABIS;

      // We notify all transactions data that all transactions finished.
      // Some may not have been really finished, in case of a timeout
      for (ProfilingTransactionData t : transactionList) {
        t.notifyFinish(
            endData.endNanos, profileStartNanos, endData.endCpuMillis, profileStartCpuMillis);
      }

      // cpu max frequencies are read with a lambda because reading files is involved, so it will be
      // done in the background when the trace file is read
      return new ProfilingTraceData(
          endData.traceFile,
          profileStartTimestamp,
          transactionList,
          transactionName,
          transactionId,
          traceId,
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
  }

  @Override
  public boolean isRunning() {
    return transactionsCounter != 0;
  }

  @Override
  public void close() {
    // we stop profiling
    if (currentProfilingTransactionData != null) {
      onTransactionFinish(
          currentProfilingTransactionData.getName(),
          currentProfilingTransactionData.getId(),
          currentProfilingTransactionData.getTraceId(),
          true,
          null,
          ScopesAdapter.getInstance().getOptions());
    } else if (transactionsCounter != 0) {
      // in case the app start profiling is running, and it's not bound to a transaction, we still
      // stop profiling, but we also have to manually update the counter.
      transactionsCounter--;
    }

    // we have to first stop profiling otherwise we would lost the last profile
    if (profiler != null) {
      profiler.close();
    }
  }

  @TestOnly
  int getTransactionsCounter() {
    return transactionsCounter;
  }
}
