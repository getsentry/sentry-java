package io.sentry.android.core;

import static io.sentry.DataCategory.All;
import static io.sentry.IConnectionStatusProvider.ConnectionStatus.DISCONNECTED;

import android.os.Build;
import androidx.annotation.RequiresApi;
import io.sentry.CompositePerformanceCollector;
import io.sentry.DataCategory;
import io.sentry.IContinuousProfiler;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.ISentryExecutorService;
import io.sentry.ISentryLifecycleToken;
import io.sentry.NoOpScopes;
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
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.SentryRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Continuous profiler that uses Android's {@link android.os.ProfilingManager} (API 35+) to capture
 * Perfetto stack-sampling traces.
 *
 * <p>This class is intentionally separate from {@link AndroidContinuousProfiler} to keep the two
 * profiling backends independent. All ProfilingManager API usage is confined to this file and
 * {@link PerfettoProfiler}.
 */
@ApiStatus.Internal
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
public class PerfettoContinuousProfiler
    implements IContinuousProfiler, RateLimiter.IRateLimitObserver {

  private static final long MAX_CHUNK_DURATION_MILLIS = 60000;

  private final @NotNull ILogger logger;
  private final @NotNull LazyEvaluator.Evaluator<ISentryExecutorService> executorServiceSupplier;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull android.content.Context appContext;
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;

  private boolean isInitialized = false;
  private @Nullable PerfettoProfiler perfettoProfiler = null;
  private boolean isRunning = false;
  private @Nullable IScopes scopes;
  private @Nullable Future<?> stopFuture;
  private @Nullable CompositePerformanceCollector performanceCollector;
  private final @NotNull List<ProfileChunk.Builder> payloadBuilders = new ArrayList<>();
  private @NotNull SentryId profilerId = SentryId.EMPTY_ID;
  private @NotNull SentryId chunkId = SentryId.EMPTY_ID;
  private final @NotNull AtomicBoolean isClosed = new AtomicBoolean(false);
  private @NotNull SentryDate startProfileChunkTimestamp = new SentryNanotimeDate();
  private volatile boolean shouldSample = true;
  private boolean shouldStop = false;
  private boolean isSampled = false;
  private int activeTraceCount = 0;

  private final AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  private final AutoClosableReentrantLock payloadLock = new AutoClosableReentrantLock();

  public PerfettoContinuousProfiler(
      final @NotNull android.content.Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector,
      final @NotNull ILogger logger,
      final @NotNull LazyEvaluator.Evaluator<ISentryExecutorService> executorServiceSupplier) {
    this.appContext = context.getApplicationContext();
    this.buildInfoProvider = buildInfoProvider;
    this.frameMetricsCollector = frameMetricsCollector;
    this.logger = logger;
    this.executorServiceSupplier = executorServiceSupplier;
  }

  @Override
  public void startProfiler(
      final @NotNull ProfileLifecycle profileLifecycle,
      final @NotNull TracesSampler tracesSampler) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (shouldSample) {
        isSampled = tracesSampler.sampleSessionProfile(SentryRandom.current().nextDouble());
        shouldSample = false;
      }
      if (!isSampled) {
        logger.log(SentryLevel.DEBUG, "Profiler was not started due to sampling decision.");
        return;
      }
      switch (profileLifecycle) {
        case TRACE:
          activeTraceCount = Math.max(0, activeTraceCount); // safety check.
          activeTraceCount++;
          break;
        case MANUAL:
          if (isRunning()) {
            logger.log(
                SentryLevel.WARNING,
                "Unexpected call to startProfiler(MANUAL) while profiler already running. Skipping.");
            return;
          }
          break;
      }
      if (!isRunning()) {
        logger.log(SentryLevel.DEBUG, "Started Profiler.");
        startInternal();
      }
    }
  }

  @Override
  public void stopProfiler(final @NotNull ProfileLifecycle profileLifecycle) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      switch (profileLifecycle) {
        case TRACE:
          activeTraceCount--;
          activeTraceCount = Math.max(0, activeTraceCount); // safety check
          // If there are active spans, and profile lifecycle is trace, we don't stop the profiler
          if (activeTraceCount > 0) {
            return;
          }
          shouldStop = true;
          break;
        case MANUAL:
          shouldStop = true;
          break;
      }
    }
  }

  /**
   * Stop the profiler as soon as we are rate limited, to avoid the performance overhead
   *
   * @param rateLimiter this {@link RateLimiter} instance which you can use to check if the rate
   *     limit is active for a specific category
   */
  @Override
  public void onRateLimitChanged(@NotNull RateLimiter rateLimiter) {
    if (rateLimiter.isActiveForCategory(All)
        || rateLimiter.isActiveForCategory(DataCategory.ProfileChunkUi)) {
      try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
        logger.log(SentryLevel.WARNING, "SDK is rate limited. Stopping profiler.");
        stopInternal(false);
      }
    }
    // If we are not rate limited anymore, we don't do anything: the profile is broken, so it's
    // useless to restart it automatically
  }

  @Override
  public void close(final boolean isTerminating) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      activeTraceCount = 0;
      shouldStop = true;
      if (isTerminating) {
        stopInternal(false);
        isClosed.set(true);
      }
    }
  }

  @Override
  public @NotNull SentryId getProfilerId() {
    return profilerId;
  }

  @Override
  public @NotNull SentryId getChunkId() {
    return chunkId;
  }

  @Override
  public boolean isRunning() {
    return isRunning;
  }

  /** Caller must hold {@link #lock}. */
  private void startInternal() {
    tryResolveScopes();
    ensureProfiler();

    if (perfettoProfiler == null) {
      return;
    }

    if (scopes != null) {
      final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
      if (rateLimiter != null
          && (rateLimiter.isActiveForCategory(All)
              || rateLimiter.isActiveForCategory(DataCategory.ProfileChunkUi))) {
        logger.log(SentryLevel.WARNING, "SDK is rate limited. Stopping profiler.");
        // Let's stop and reset profiler id, as the profile is now broken anyway
        stopInternal(false);
        return;
      }

      // If device is offline, we don't start the profiler, to avoid flooding the cache
      // TODO .getConnectionStatus() may be blocking, investigate if this can be done async
      if (scopes.getOptions().getConnectionStatusProvider().getConnectionStatus() == DISCONNECTED) {
        logger.log(SentryLevel.WARNING, "Device is offline. Stopping profiler.");
        // Let's stop and reset profiler id, as the profile is now broken anyway
        stopInternal(false);
        return;
      }
      startProfileChunkTimestamp = scopes.getOptions().getDateProvider().now();
    } else {
      startProfileChunkTimestamp = new SentryNanotimeDate();
    }

    final AndroidProfiler.ProfileStartData startData =
        perfettoProfiler.start(MAX_CHUNK_DURATION_MILLIS);
    // check if profiling started
    if (startData == null) {
      return;
    }

    isRunning = true;

    if (profilerId.equals(SentryId.EMPTY_ID)) {
      profilerId = new SentryId();
    }

    if (chunkId.equals(SentryId.EMPTY_ID)) {
      chunkId = new SentryId();
    }

    if (performanceCollector != null) {
      performanceCollector.start(chunkId.toString());
    }

    try {
      stopFuture =
          executorServiceSupplier
              .evaluate()
              .schedule(
                  () -> {
                    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
                      stopInternal(true);
                    }
                  },
                  MAX_CHUNK_DURATION_MILLIS);
    } catch (RejectedExecutionException e) {
      logger.log(
          SentryLevel.ERROR,
          "Failed to schedule profiling chunk finish. Did you call Sentry.close()?",
          e);
      shouldStop = true;
    }
  }

  /** Caller must hold {@link #lock}. */
  private void stopInternal(final boolean restartProfiler) {
    tryResolveScopes();
    if (stopFuture != null) {
      stopFuture.cancel(false);
    }
    // check if profiler was created and it's running
    if (perfettoProfiler == null || !isRunning) {
      // When the profiler is stopped due to an error (e.g. offline or rate limited), reset the
      // ids
      profilerId = SentryId.EMPTY_ID;
      chunkId = SentryId.EMPTY_ID;
      return;
    }

    final AndroidProfiler.ProfileEndData endData = perfettoProfiler.endAndCollect();

    // check if profiler ended successfully
    if (endData == null) {
      logger.log(
          SentryLevel.ERROR,
          "An error occurred while collecting a profile chunk, and it won't be sent.");
    } else {
      // The scopes can be null if the profiler is started before the SDK is initialized (app
      // start profiling), meaning there's no scopes to send the chunks. In that case, we store
      // the data in a list and send it when the next chunk is finished.
      try (final @NotNull ISentryLifecycleToken ignored2 = payloadLock.acquire()) {
        final ProfileChunk.Builder builder =
            new ProfileChunk.Builder(
                profilerId,
                chunkId,
                endData.measurementsMap,
                endData.traceFile,
                startProfileChunkTimestamp,
                ProfileChunk.PLATFORM_ANDROID);
        builder.setContentType("perfetto");
        payloadBuilders.add(builder);
      }
    }

    isRunning = false;
    // A chunk is finished. Next chunk will have a different id.
    chunkId = SentryId.EMPTY_ID;

    if (scopes != null) {
      sendChunks(scopes, scopes.getOptions());
    }

    if (restartProfiler && !shouldStop) {
      logger.log(SentryLevel.DEBUG, "Profile chunk finished. Starting a new one.");
      startInternal();
    } else {
      // When the profiler is stopped manually, we have to reset its id
      profilerId = SentryId.EMPTY_ID;
      logger.log(SentryLevel.DEBUG, "Profile chunk finished.");
    }
  }

  private void tryResolveScopes() {
    if ((scopes == null || scopes == NoOpScopes.getInstance())
        && Sentry.getCurrentScopes() != NoOpScopes.getInstance()) {
      onScopesAvailable(Sentry.getCurrentScopes());
    }
  }

  private void onScopesAvailable(final @NotNull IScopes resolvedScopes) {
    this.scopes = resolvedScopes;
    this.performanceCollector = resolvedScopes.getOptions().getCompositePerformanceCollector();
    final @Nullable RateLimiter rateLimiter = resolvedScopes.getRateLimiter();
    if (rateLimiter != null) {
      rateLimiter.addRateLimitObserver(this);
    }
  }

  private void ensureProfiler() {
    logger.log(
        SentryLevel.DEBUG,
        "PerfettoContinuousProfiler.ensureProfiler() isInitialized=%s, apiLevel=%d",
        isInitialized,
        buildInfoProvider.getSdkInfoVersion());

    if (!isInitialized) {
      perfettoProfiler = new PerfettoProfiler(appContext, frameMetricsCollector, logger);
      isInitialized = true;
    }
  }

  public void reevaluateSampling() {
    shouldSample = true;
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
                try (final @NotNull ISentryLifecycleToken ignored = payloadLock.acquire()) {
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

  @VisibleForTesting
  @Nullable
  Future<?> getStopFuture() {
    return stopFuture;
  }

  @VisibleForTesting
  public int getActiveTraceCount() {
    return activeTraceCount;
  }
}
