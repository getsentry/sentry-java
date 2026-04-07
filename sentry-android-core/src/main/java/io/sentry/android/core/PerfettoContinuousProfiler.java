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
import io.sentry.SentryOptions;
import io.sentry.TracesSampler;
import io.sentry.protocol.SentryId;
import io.sentry.transport.RateLimiter;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.SentryRandom;
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
 * profiling backends independent. All ProfilingManager API usage is confined to this file and {@link
 * PerfettoProfiler}.
 *
 * <p>Currently, this class doesn't do app-start profiling {@link SentryPerformanceProvider}.
 * It is created during {@code Sentry.init()}.
 *
 * <p>Thread safety: all mutable state is guarded by a single {@link
 * io.sentry.util.AutoClosableReentrantLock}. Public entry points ({@link #startProfiler}, {@link
 * #stopProfiler}, {@link #close}, {@link #onRateLimitChanged}, {@link #reevaluateSampling}, and
 * the getters) acquire the lock themselves and are thread-safe.
 * Private methods {@code startInternal} and {@code stopInternal} require the caller to hold the lock.
 */
@ApiStatus.Internal
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
public class PerfettoContinuousProfiler
    implements IContinuousProfiler, RateLimiter.IRateLimitObserver {
  private static final long MAX_CHUNK_DURATION_MILLIS = 60000;

  private final @NotNull ILogger logger;
  private final @NotNull LazyEvaluator.Evaluator<ISentryExecutorService> executorServiceSupplier;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull LazyEvaluator.Evaluator<PerfettoProfiler> perfettoProfilerSupplier;

  private @Nullable PerfettoProfiler perfettoProfiler = null;
  private boolean isRunning = false;
  private @Nullable IScopes scopes;
  private @Nullable CompositePerformanceCollector performanceCollector;
  private @Nullable Future<?> stopFuture;
  private @NotNull SentryId profilerId = SentryId.EMPTY_ID;
  private @NotNull SentryId chunkId = SentryId.EMPTY_ID;
  private final @NotNull AtomicBoolean isClosed = new AtomicBoolean(false);
  private @NotNull SentryDate startProfileChunkTimestamp =
      new io.sentry.SentryNanotimeDate();
  private boolean shouldSample = true;
  private boolean shouldStop = false;
  private boolean isSampled = false;
  private int activeTraceCount = 0;

  private final AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public PerfettoContinuousProfiler(
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull ILogger logger,
      final @NotNull LazyEvaluator.Evaluator<ISentryExecutorService> executorServiceSupplier,
      final @NotNull LazyEvaluator.Evaluator<PerfettoProfiler> perfettoProfilerSupplier) {
    this.buildInfoProvider = buildInfoProvider;
    this.logger = logger;
    this.executorServiceSupplier = executorServiceSupplier;
    this.perfettoProfilerSupplier = perfettoProfilerSupplier;
  }

  @Override
  public void startProfiler(
      final @NotNull ProfileLifecycle profileLifecycle,
      final @NotNull TracesSampler tracesSampler) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      shouldStop = false;
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
   * Stop the profiler as soon as we are rate limited, to avoid the performance overhead.
   *
   * @param rateLimiter the {@link RateLimiter} instance to check categories against
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
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return profilerId;
    }
  }

  @Override
  public @NotNull SentryId getChunkId() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return chunkId;
    }
  }

  @Override
  public boolean isRunning() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return isRunning;
    }
  }

  /**
   * Resolves scopes on first call. Since PerfettoContinuousProfiler is created during
   * Sentry.init() and never used for app-start profiling, scopes is guaranteed to be available by
   * the time startProfiler is called.
   *
   * <p>Caller must hold {@link #lock}.
   */
  private @NotNull IScopes resolveScopes() {
    if (scopes != null && scopes != NoOpScopes.getInstance()) {
      return scopes;
    }
    final @NotNull IScopes currentScopes = Sentry.getCurrentScopes();
    if (currentScopes == NoOpScopes.getInstance()) {
      logger.log(
          SentryLevel.ERROR,
          "PerfettoContinuousProfiler: scopes not available. This is unexpected.");
      return currentScopes;
    }
    this.scopes = currentScopes;
    this.performanceCollector = currentScopes.getOptions().getCompositePerformanceCollector();
    final @Nullable RateLimiter rateLimiter = currentScopes.getRateLimiter();
    if (rateLimiter != null) {
      rateLimiter.addRateLimitObserver(this);
    }
    return scopes;
  }

  /** Caller must hold {@link #lock}. */
  private void startInternal() {
    final @NotNull IScopes scopes = resolveScopes();
    ensureProfiler();

    if (perfettoProfiler == null) {
      return;
    }

    final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
    if (rateLimiter != null
        && (rateLimiter.isActiveForCategory(All)
            || rateLimiter.isActiveForCategory(DataCategory.ProfileChunkUi))) {
      logger.log(SentryLevel.WARNING, "SDK is rate limited. Stopping profiler.");
      stopInternal(false);
      return;
    }

    // If device is offline, we don't start the profiler, to avoid flooding the cache
    if (scopes.getOptions().getConnectionStatusProvider().getConnectionStatus() == DISCONNECTED) {
      logger.log(SentryLevel.WARNING, "Device is offline. Stopping profiler.");
      stopInternal(false);
      return;
    }

    startProfileChunkTimestamp = scopes.getOptions().getDateProvider().now();

    final AndroidProfiler.ProfileStartData startData =
        perfettoProfiler.start(MAX_CHUNK_DURATION_MILLIS);
    if (startData == null) {
      logger.log(
          SentryLevel.ERROR,
          "Failed to start Perfetto profiling. PerfettoProfiler.start() returned null.");
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
    if (stopFuture != null) {
      stopFuture.cancel(false);
    }
    // check if profiler was created and it's running
    if (perfettoProfiler == null || !isRunning) {
      profilerId = SentryId.EMPTY_ID;
      chunkId = SentryId.EMPTY_ID;
      return;
    }

    final @NotNull IScopes scopes = resolveScopes();
    final @NotNull SentryOptions options = scopes.getOptions();

    if (performanceCollector != null) {
      performanceCollector.stop(chunkId.toString());
    }

    final AndroidProfiler.ProfileEndData endData = perfettoProfiler.endAndCollect();

    // check if profiler ended successfully
    if (endData == null) {
      logger.log(
          SentryLevel.ERROR,
          "An error occurred while collecting a profile chunk, and it won't be sent.");
    } else {
      final ProfileChunk.Builder builder =
          new ProfileChunk.Builder(
              profilerId,
              chunkId,
              endData.measurementsMap,
              endData.traceFile,
              startProfileChunkTimestamp,
              ProfileChunk.PLATFORM_ANDROID);
      builder.setContentType("perfetto");
      sendChunk(builder, scopes, options);
    }

    isRunning = false;
    // A chunk is finished. Next chunk will have a different id.
    chunkId = SentryId.EMPTY_ID;

    if (restartProfiler && !shouldStop) {
      logger.log(SentryLevel.DEBUG, "Profile chunk finished. Starting a new one.");
      startInternal();
    } else {
      // When the profiler is stopped manually, we have to reset its id
      profilerId = SentryId.EMPTY_ID;
      logger.log(SentryLevel.DEBUG, "Profile chunk finished.");
    }
  }

  private void ensureProfiler() {
    if (perfettoProfiler == null) {
      logger.log(
          SentryLevel.DEBUG,
          "PerfettoContinuousProfiler: creating PerfettoProfiler (apiLevel=%d)",
          buildInfoProvider.getSdkInfoVersion());
      perfettoProfiler = perfettoProfilerSupplier.evaluate();
    }
  }

  public void reevaluateSampling() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      shouldSample = true;
    }
  }

  private void sendChunk(
      final @NotNull ProfileChunk.Builder builder,
      final @NotNull IScopes scopes,
      final @NotNull SentryOptions options) {
    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                if (isClosed.get()) {
                  return;
                }
                scopes.captureProfileChunk(builder.build(options));
              });
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.DEBUG, "Failed to send profile chunk.", e);
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
