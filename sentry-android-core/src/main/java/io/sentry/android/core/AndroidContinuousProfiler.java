package io.sentry.android.core;

import static io.sentry.DataCategory.All;
import static io.sentry.IConnectionStatusProvider.ConnectionStatus.DISCONNECTED;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.os.Build;
import io.sentry.CompositePerformanceCollector;
import io.sentry.DataCategory;
import io.sentry.IContinuousProfiler;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.ISentryExecutorService;
import io.sentry.NoOpScopes;
import io.sentry.PerformanceCollectionData;
import io.sentry.ProfileChunk;
import io.sentry.ProfileLifecycle;
import io.sentry.Sentry;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryNanotimeDate;
import io.sentry.SentryOptions;
import io.sentry.TracesSampler;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.protocol.SentryId;
import io.sentry.transport.RateLimiter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public class AndroidContinuousProfiler
    implements IContinuousProfiler, RateLimiter.IRateLimitObserver {
  private static final long MAX_CHUNK_DURATION_MILLIS = 60000;

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
  private @Nullable CompositePerformanceCollector performanceCollector;
  private final @NotNull List<ProfileChunk.Builder> payloadBuilders = new ArrayList<>();
  private @NotNull SentryId profilerId = SentryId.EMPTY_ID;
  private @NotNull SentryId chunkId = SentryId.EMPTY_ID;
  private final @NotNull AtomicBoolean isClosed = new AtomicBoolean(false);
  private @NotNull SentryDate startProfileChunkTimestamp = new SentryNanotimeDate();
  private boolean shouldSample = true;
  private boolean isSampled = false;
  private int rootSpanCounter = 0;

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
            logger);
  }

  @Override
  public synchronized void startProfileSession(
      final @NotNull ProfileLifecycle profileLifecycle,
      final @NotNull TracesSampler tracesSampler) {
    if (shouldSample) {
      isSampled = tracesSampler.sampleSessionProfile();
      shouldSample = false;
    }
    if (!isSampled) {
      logger.log(SentryLevel.DEBUG, "Profiler was not started due to sampling decision.");
      return;
    }
    switch (profileLifecycle) {
      case TRACE:
        // rootSpanCounter should never be negative, unless the user changed profile lifecycle while
        // the profiler is running or close() is called. This is just a safety check.
        if (rootSpanCounter < 0) {
          rootSpanCounter = 0;
        }
        rootSpanCounter++;
        break;
      case MANUAL:
        // We check if the profiler is already running and log a message only in manual mode, since
        // in trace mode we can have multiple concurrent traces
        if (isRunning()) {
          logger.log(SentryLevel.DEBUG, "Profiler is already running.");
          return;
        }
        break;
    }
    if (!isRunning()) {
      logger.log(SentryLevel.DEBUG, "Started Profiler.");
      start();
    }
  }

  private synchronized void start() {
    if ((scopes == null || scopes == NoOpScopes.getInstance())
        && Sentry.getCurrentScopes() != NoOpScopes.getInstance()) {
      this.scopes = Sentry.getCurrentScopes();
      this.performanceCollector =
          Sentry.getCurrentScopes().getOptions().getCompositePerformanceCollector();
      final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
      if (rateLimiter != null) {
        rateLimiter.addRateLimitObserver(this);
      }
    }

    // Debug.startMethodTracingSampling() is only available since Lollipop, but Android Profiler
    // causes crashes on api 21 -> https://github.com/getsentry/sentry-java/issues/3392
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP_MR1) return;

    // Let's initialize trace folder and profiling interval
    init();
    // init() didn't create profiler, should never happen
    if (profiler == null) {
      return;
    }

    if (scopes != null) {
      final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
      if (rateLimiter != null
          && (rateLimiter.isActiveForCategory(All)
              || rateLimiter.isActiveForCategory(DataCategory.ProfileChunk))) {
        logger.log(SentryLevel.WARNING, "SDK is rate limited. Stopping profiler.");
        // Let's stop and reset profiler id, as the profile is now broken anyway
        stop(false);
        return;
      }

      // If device is offline, we don't start the profiler, to avoid flooding the cache
      if (scopes.getOptions().getConnectionStatusProvider().getConnectionStatus() == DISCONNECTED) {
        logger.log(SentryLevel.WARNING, "Device is offline. Stopping profiler.");
        // Let's stop and reset profiler id, as the profile is now broken anyway
        stop(false);
        return;
      }
      startProfileChunkTimestamp = scopes.getOptions().getDateProvider().now();
    } else {
      startProfileChunkTimestamp = new SentryNanotimeDate();
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

    try {
      stopFuture = executorService.schedule(() -> stop(true), MAX_CHUNK_DURATION_MILLIS);
    } catch (RejectedExecutionException e) {
      logger.log(
          SentryLevel.ERROR,
          "Failed to schedule profiling chunk finish. Did you call Sentry.close()?",
          e);
    }
  }

  @Override
  public synchronized void stopProfileSession(final @NotNull ProfileLifecycle profileLifecycle) {
    switch (profileLifecycle) {
      case TRACE:
        rootSpanCounter--;
        // If there are active spans, and profile lifecycle is trace, we don't stop the profiler
        if (rootSpanCounter > 0) {
          return;
        }
        // rootSpanCounter should never be negative, unless the user changed profile lifecycle while
        // the profiler is running or close() is called. This is just a safety check.
        if (rootSpanCounter < 0) {
          rootSpanCounter = 0;
        }
        stop(false);
        break;
      case MANUAL:
        stop(false);
        break;
    }
  }

  private synchronized void stop(final boolean restartProfiler) {
    if (stopFuture != null) {
      stopFuture.cancel(true);
    }
    // check if profiler was created and it's running
    if (profiler == null || !isRunning) {
      // When the profiler is stopped due to an error (e.g. offline or rate limited), reset the ids
      profilerId = SentryId.EMPTY_ID;
      chunkId = SentryId.EMPTY_ID;
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
      logger.log(
          SentryLevel.ERROR,
          "An error occurred while collecting a profile chunk, and it won't be sent.");
    } else {
      // The scopes can be null if the profiler is started before the SDK is initialized (app start
      //  profiling), meaning there's no scopes to send the chunks. In that case, we store the data
      //  in a list and send it when the next chunk is finished.
      synchronized (payloadBuilders) {
        payloadBuilders.add(
            new ProfileChunk.Builder(
                profilerId,
                chunkId,
                endData.measurementsMap,
                endData.traceFile,
                startProfileChunkTimestamp));
      }
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

  public synchronized void reevaluateSampling() {
    shouldSample = true;
  }

  public synchronized void close() {
    rootSpanCounter = 0;
    stop(false);
    isClosed.set(true);
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
                // SDK is closed, we don't send the chunks
                if (isClosed.get()) {
                  return;
                }
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

  @VisibleForTesting
  public int getRootSpanCounter() {
    return rootSpanCounter;
  }

  @Override
  public void onRateLimitChanged(@NotNull RateLimiter rateLimiter) {
    // We stop the profiler as soon as we are rate limited, to avoid the performance overhead
    if (rateLimiter.isActiveForCategory(All)
        || rateLimiter.isActiveForCategory(DataCategory.ProfileChunk)) {
      logger.log(SentryLevel.WARNING, "SDK is rate limited. Stopping profiler.");
      stop(false);
    }
    // If we are not rate limited anymore, we don't do anything: the profile is broken, so it's
    // useless to restart it automatically
  }
}
