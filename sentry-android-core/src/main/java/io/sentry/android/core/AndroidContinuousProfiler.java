package io.sentry.android.core;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.os.Build;
import io.sentry.IContinuousProfiler;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.ISentryExecutorService;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import java.util.concurrent.Future;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public class AndroidContinuousProfiler implements IContinuousProfiler {
  private static final long MAX_CHUNK_DURATION_MILLIS = 10000;

  private final @NotNull ILogger logger;
  private final @Nullable String profilingTracesDirPath;
  private final boolean isProfilingEnabled;
  private final int profilingTracesHz;
  private final @NotNull ISentryExecutorService executorService;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private boolean isInitialized = false;
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;
  private @Nullable AndroidProfiler profiler = null;
  private boolean isRunning = false;
  private @Nullable IScopes scopes;
  private @Nullable Future<?> closeFuture;

  public AndroidContinuousProfiler(
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector,
      final @NotNull ILogger logger,
      final @Nullable String profilingTracesDirPath,
      final boolean isProfilingEnabled,
      final int profilingTracesHz,
      final @NotNull ISentryExecutorService executorService) {
    this.logger = logger;
    this.frameMetricsCollector = frameMetricsCollector;
    this.buildInfoProvider = buildInfoProvider;
    this.profilingTracesDirPath = profilingTracesDirPath;
    this.isProfilingEnabled = isProfilingEnabled;
    this.profilingTracesHz = profilingTracesHz;
    this.executorService = executorService;
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
            null,
            logger,
            buildInfoProvider);
  }

  public synchronized void setScopes(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  public synchronized void start() {
    // Debug.startMethodTracingSampling() is only available since Lollipop, but Android Profiler
    // causes crashes on api 21 -> https://github.com/getsentry/sentry-java/issues/3392
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP_MR1) return;

    // Let's initialize trace folder and profiling interval
    init();
    // init() didn't create profiler, should never happen
    if (profiler == null) {
      return;
    }

    final AndroidProfiler.ProfileStartData startData = profiler.start();
    // check if profiling started
    if (startData == null) {
      return;
    }
    isRunning = true;

    closeFuture = executorService.schedule(() -> stop(true), MAX_CHUNK_DURATION_MILLIS);
  }

  public synchronized void stop() {
    stop(false);
  }

  @SuppressLint("NewApi")
  private synchronized void stop(final boolean restartProfiler) {
    if (closeFuture != null) {
      closeFuture.cancel(true);
    }
    // check if profiler was created and it's running
    if (profiler == null || !isRunning) {
      return;
    }

    // onTransactionStart() is only available since Lollipop_MR1
    // and SystemClock.elapsedRealtimeNanos() since Jelly Bean
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP_MR1) {
      return;
    }

    // todo add PerformanceCollectionData
    final AndroidProfiler.ProfileEndData endData = profiler.endAndCollect(false, null);

    // check if profiler end successfully
    if (endData == null) {
      return;
    }

    isRunning = false;

    // todo schedule capture profile chunk envelope

    if (restartProfiler) {
      logger.log(SentryLevel.DEBUG, "Profile chunk finished. Starting a new one.");
      start();
    } else {
      logger.log(SentryLevel.DEBUG, "Profile chunk finished.");
    }
  }

  public synchronized void close() {
    if (closeFuture != null) {
      closeFuture.cancel(true);
    }
    stop();
  }

  @Override
  public boolean isRunning() {
    return isRunning;
  }

  @VisibleForTesting
  @Nullable
  Future<?> getCloseFuture() {
    return closeFuture;
  }
}
