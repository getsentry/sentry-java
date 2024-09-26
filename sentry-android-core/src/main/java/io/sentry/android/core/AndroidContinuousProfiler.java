package io.sentry.android.core;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.os.Build;
import io.sentry.IContinuousProfiler;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.ISentryExecutorService;
import io.sentry.PerformanceCollectionData;
import io.sentry.ProfileChunk;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.TransactionPerformanceCollector;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.protocol.SentryId;
import java.util.ArrayList;
import java.util.List;
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
  private final int profilingTracesHz;
  private final @NotNull ISentryExecutorService executorService;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private boolean isInitialized = false;
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;
  private @Nullable AndroidProfiler profiler = null;
  private boolean isRunning = false;
  private @Nullable IScopes scopes;
  private @Nullable Future<?> stopFuture;
  private @Nullable TransactionPerformanceCollector performanceCollector;
  private final @NotNull List<ProfileChunk.Builder> payloadBuilders = new ArrayList<>();
  private @NotNull SentryId profilerId = SentryId.EMPTY_ID;
  private @NotNull SentryId chunkId = SentryId.EMPTY_ID;

  public AndroidContinuousProfiler(
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector,
      final @NotNull ILogger logger,
      final @Nullable String profilingTracesDirPath,
      final int profilingTracesHz,
      final @NotNull ISentryExecutorService executorService) {
    this.logger = logger;
    this.frameMetricsCollector = frameMetricsCollector;
    this.buildInfoProvider = buildInfoProvider;
    this.profilingTracesDirPath = profilingTracesDirPath;
    this.profilingTracesHz = profilingTracesHz;
    this.executorService = executorService;
  }

  private void init() {
    // We initialize it only once
    if (isInitialized) {
      return;
    }
    isInitialized = true;
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
    this.performanceCollector = scopes.getOptions().getTransactionPerformanceCollector();
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

    if (profilerId == SentryId.EMPTY_ID) {
      profilerId = new SentryId();
    }

    if (chunkId == SentryId.EMPTY_ID) {
      chunkId = new SentryId();
    }

    if (performanceCollector != null) {
      performanceCollector.start(chunkId.toString());
    }

    stopFuture = executorService.schedule(() -> stop(true), MAX_CHUNK_DURATION_MILLIS);
  }

  public synchronized void stop() {
    stop(false);
  }

  @SuppressLint("NewApi")
  private synchronized void stop(final boolean restartProfiler) {
    if (stopFuture != null) {
      stopFuture.cancel(true);
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

    List<PerformanceCollectionData> performanceCollectionData = null;
    if (performanceCollector != null) {
      performanceCollectionData = performanceCollector.stop(chunkId.toString());
    }

    final AndroidProfiler.ProfileEndData endData =
        profiler.endAndCollect(false, performanceCollectionData);

    // check if profiler end successfully
    if (endData == null) {
      return;
    }

    // The scopes can be null if the profiler is started before the SDK is initialized (app start
    //  profiling), meaning there's no scopes to send the chunks. In that case, we store the data
    //  in a list and send it when the next chunk is finished.
    synchronized (payloadBuilders) {
      payloadBuilders.add(
          new ProfileChunk.Builder(
              profilerId, chunkId, endData.measurementsMap, endData.traceFile));
    }

    isRunning = false;
    // A chunk is finished. Next chunk will have a different id.
    chunkId = SentryId.EMPTY_ID;

    if (scopes != null) {
      sendChunks(scopes, scopes.getOptions());
    }

    if (restartProfiler) {
      logger.log(SentryLevel.DEBUG, "Profile chunk finished. Starting a new one.");
      start();
    } else {
      // When the profiler is stopped manually, we have to reset its id
      profilerId = SentryId.EMPTY_ID;
      logger.log(SentryLevel.DEBUG, "Profile chunk finished.");
    }
  }

  public synchronized void close() {
    if (stopFuture != null) {
      stopFuture.cancel(true);
    }
    stop();
  }

  @Override
  public @NotNull SentryId getProfilerId() {
    return profilerId;
  }

  private void sendChunks(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                final ArrayList<ProfileChunk> payloads = new ArrayList<>(payloadBuilders.size());
                synchronized (payloadBuilders) {
                  for (ProfileChunk.Builder builder : payloadBuilders) {
                    payloads.add(builder.build(options));
                  }
                  payloadBuilders.clear();
                }
                for (ProfileChunk payload : payloads) {
                  scopes.captureProfileChunk(payload);
                }
              });
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.DEBUG, "Failed to send profile chunks.", e);
    }
  }

  @Override
  public boolean isRunning() {
    return isRunning;
  }

  @VisibleForTesting
  @Nullable
  Future<?> getStopFuture() {
    return stopFuture;
  }
}
