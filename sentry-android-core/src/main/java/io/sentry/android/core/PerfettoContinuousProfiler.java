package io.sentry.android.core;

import static io.sentry.DataCategory.All;
import static io.sentry.IConnectionStatusProvider.ConnectionStatus.DISCONNECTED;

import android.os.Build;
import android.os.SystemClock;
import androidx.annotation.RequiresApi;
import io.sentry.CompositePerformanceCollector;
import io.sentry.DataCategory;
import io.sentry.IContinuousProfiler;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.ISentryExecutorService;
import io.sentry.ISentryLifecycleToken;
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
import io.sentry.android.core.internal.profiling.ChunkRecord;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.profilemeasurements.ProfileMeasurement;
import io.sentry.profilemeasurements.ProfileMeasurementValue;
import io.sentry.profiling.ProfileRecordingState;
import io.sentry.protocol.SentryId;
import io.sentry.transport.RateLimiter;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.SentryRandom;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
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
 *
 * <p>Currently, this class doesn't do app-start profiling {@link SentryPerformanceProvider}. It is
 * created during {@code Sentry.init()}.
 *
 * <p>Thread safety: the profiler state is guarded by {@link #lock}. Every public entry point
 * acquires it itself and is thread-safe. Private methods that say {@code Caller must hold} a lock
 * do not, and must only be reached from a frame that already holds it.
 *
 * <p>The chunk history is guarded by its own {@link #chunkHistoryLock}, so that {@link
 * #getProfileRecordingState} — called for every span of a finishing transaction — never waits for a
 * chunk start or a chunk stop. A frame holding {@link #lock} may take {@link #chunkHistoryLock},
 * never the other way around. Each {@link ChunkRecord} guards its own state, as the profiler writes
 * the outcome of a running chunk into it.
 */
@ApiStatus.Internal
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
public class PerfettoContinuousProfiler
    implements IContinuousProfiler, RateLimiter.IRateLimitObserver {
  private static final long MAX_CHUNK_DURATION_MILLIS = 60000;

  /**
   * How many chunks we remember the outcome of. Spans only ask about windows they were running in,
   * so a handful of chunks (a minute each) is plenty.
   *
   * <p>The history is therefore assumed to be long enough for every window a span can ask about. A
   * window whose chunks all fell out of it needs no special treatment: it reads as a window no
   * chunk covers, and keeps its profiler id.
   */
  @VisibleForTesting static final int MAX_CHUNK_HISTORY_SIZE = 10;

  // Matches the thread name produced by SentryExecutorService's thread factory, used to detect
  // when we are already running on the executor thread.
  private static final String EXECUTOR_THREAD_NAME_PREFIX = "SentryExecutorServiceThreadFactory";

  private final @NotNull ILogger logger;
  private final @NotNull LazyEvaluator.Evaluator<ISentryExecutorService> executorServiceSupplier;
  private final @NotNull Supplier<PerfettoProfiler> perfettoProfilerFactory;

  private @Nullable PerfettoProfiler perfettoProfiler = null;
  private final @NotNull ChunkMeasurementCollector chunkMeasurements;
  private boolean isRunning = false;
  private @Nullable IScopes scopes;
  private @Nullable CompositePerformanceCollector performanceCollector;
  private @Nullable Future<?> stopFuture;
  private @NotNull SentryId profilerId = SentryId.EMPTY_ID;
  private @NotNull SentryId chunkId = SentryId.EMPTY_ID;
  private final @NotNull AtomicBoolean isClosed = new AtomicBoolean(false);
  private @NotNull SentryDate startProfileChunkTimestamp = new io.sentry.SentryNanotimeDate();
  private boolean shouldSample = true;
  private boolean shouldStop = false;
  private boolean isSampled = false;
  private int activeTraceCount = 0;

  private final AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  private final @NotNull ArrayDeque<ChunkRecord> chunkHistory =
      new ArrayDeque<>(MAX_CHUNK_HISTORY_SIZE);

  private final AutoClosableReentrantLock chunkHistoryLock = new AutoClosableReentrantLock();

  public PerfettoContinuousProfiler(
      final @NotNull ILogger logger,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector,
      final @NotNull LazyEvaluator.Evaluator<ISentryExecutorService> executorServiceSupplier,
      final @NotNull Supplier<PerfettoProfiler> perfettoProfilerFactory) {
    this.logger = logger;
    this.chunkMeasurements = new ChunkMeasurementCollector(frameMetricsCollector);
    this.executorServiceSupplier = executorServiceSupplier;
    this.perfettoProfilerFactory = perfettoProfilerFactory;
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
        shouldStop = false;
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
        // Closing first, so that a result the OS already delivered cannot mark the chunk that
        // stopInternal ends as recorded: nothing is sent once the profiler is closed
        isClosed.set(true);
        stopInternal(false);
        // The chunk that just ended covers nothing, and a pending collection cannot change that
        markLastChunkNotRecordedIfUnknown();
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

  @Override
  public @NotNull ProfileRecordingState getProfileRecordingState(
      final @NotNull SentryId profilerId,
      final @NotNull SentryDate startTime,
      final @NotNull SentryDate endTime) {
    try (final @NotNull ISentryLifecycleToken ignored = chunkHistoryLock.acquire()) {
      boolean hasFailedChunk = false;
      boolean hasUnknownChunk = false;

      for (final @NotNull ChunkRecord chunk : chunkHistory) {
        if (!chunk.getProfilerId().equals(profilerId) || !chunk.overlaps(startTime, endTime)) {
          continue;
        }
        final @NotNull ProfileRecordingState state = chunk.getRecordingState();
        if (state == ProfileRecordingState.RECORDED) {
          return ProfileRecordingState.RECORDED;
        }
        if (state == ProfileRecordingState.UNKNOWN) {
          hasUnknownChunk = true;
        } else {
          hasFailedChunk = true;
        }
      }

      // A chunk that is still running, or that is still being collected, may yet be recorded
      if (hasUnknownChunk) {
        return ProfileRecordingState.UNKNOWN;
      }

      // Every chunk covering the window failed
      if (hasFailedChunk) {
        return ProfileRecordingState.NOT_RECORDED;
      }

      // No chunk covers the window. It may have fallen out of the history, or a back-dated span may
      // read as outside a chunk it really ran in, so the window gets the benefit of the doubt
      return ProfileRecordingState.UNKNOWN;
    }
  }

  /**
   * Gives up on the newest chunk, unless its outcome is already known. Only that one can still be
   * unknown, as a chunk gets its outcome before the next one starts.
   */
  private void markLastChunkNotRecordedIfUnknown() {
    try (final @NotNull ISentryLifecycleToken ignored = chunkHistoryLock.acquire()) {
      final @Nullable ChunkRecord lastChunk = chunkHistory.peekLast();
      if (lastChunk != null && lastChunk.getRecordingState() == ProfileRecordingState.UNKNOWN) {
        lastChunk.setRecordingState(ProfileRecordingState.NOT_RECORDED);
      }
    }
  }

  private void addChunkRecord(final @NotNull ChunkRecord chunk) {
    try (final @NotNull ISentryLifecycleToken ignored = chunkHistoryLock.acquire()) {
      if (chunkHistory.size() == MAX_CHUNK_HISTORY_SIZE) {
        chunkHistory.removeFirst();
      }
      chunkHistory.addLast(chunk);
    }
  }

  private void removeChunkRecord(final @NotNull ChunkRecord chunk) {
    try (final @NotNull ISentryLifecycleToken ignored = chunkHistoryLock.acquire()) {
      chunkHistory.remove(chunk);
    }
  }

  /**
   * Ends the running chunk, which is always the newest one, as a chunk only starts once the one
   * before it ended. Returns null if there is none, or if it already ended.
   */
  private @Nullable ChunkRecord endChunkRecord(final @NotNull SentryDate endTimestamp) {
    try (final @NotNull ISentryLifecycleToken ignored = chunkHistoryLock.acquire()) {
      final @Nullable ChunkRecord chunk = chunkHistory.peekLast();
      if (chunk == null || chunk.hasEnded()) {
        return null;
      }
      chunk.setEndTimestamp(endTimestamp);
      return chunk;
    }
  }

  /**
   * Resolves scopes on first call. Since PerfettoContinuousProfiler is created during Sentry.init()
   * and never used for app-start profiling, scopes is guaranteed to be available by the time
   * startProfiler is called.
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

    perfettoProfiler = perfettoProfilerFactory.get();
    if (perfettoProfiler == null) {
      return;
    }
    if (SentryId.EMPTY_ID.equals(profilerId)) {
      profilerId = new SentryId();
    }

    // The chunk is known before profiling starts, so that a transaction finishing right after the
    // request cannot miss the chunk it ran in and drop its profiler id
    final @NotNull ChunkRecord chunkRecord =
        new ChunkRecord(profilerId, startProfileChunkTimestamp);
    addChunkRecord(chunkRecord);

    if (!perfettoProfiler.start(chunkRecord, MAX_CHUNK_DURATION_MILLIS)) {
      // No chunk ran, so the record is dropped rather than kept as a failure: it would take a slot
      // of the history away from the chunks that did run
      removeChunkRecord(chunkRecord);
      profilerId = SentryId.EMPTY_ID;
      chunkId = SentryId.EMPTY_ID;
      logger.log(SentryLevel.ERROR, "Failed to start Perfetto profiling.");
      return;
    }

    isRunning = true;
    if (chunkId.equals(SentryId.EMPTY_ID)) {
      chunkId = new SentryId();
    }

    chunkMeasurements.start(performanceCollector, chunkId.toString());

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
    final @Nullable PerfettoProfiler currentProfiler = perfettoProfiler;

    if (stopFuture != null) {
      stopFuture.cancel(false);
    }

    // Make sure perfetto was running
    if (currentProfiler == null || !isRunning) {
      profilerId = SentryId.EMPTY_ID;
      chunkId = SentryId.EMPTY_ID;
      return;
    }

    final @NotNull IScopes scopes = resolveScopes();
    final @NotNull SentryOptions options = scopes.getOptions();

    final @NotNull Map<String, ProfileMeasurement> measurements = chunkMeasurements.stop();

    // Capture state needed by the callback before clearing it
    final @NotNull SentryId chunkProfilerId = profilerId;
    final @NotNull SentryId chunkChunkId = chunkId;
    final @NotNull SentryDate chunkTimestamp = startProfileChunkTimestamp;
    final @Nullable ChunkRecord chunkRecord = endChunkRecord(options.getDateProvider().now());

    isRunning = false;
    perfettoProfiler = null;
    chunkId = SentryId.EMPTY_ID;

    if (!restartProfiler || shouldStop) {
      profilerId = SentryId.EMPTY_ID;
    }

    final boolean shouldRestart = restartProfiler && !shouldStop;

    // endAndCollect is non-blocking: the listener fires when the OS delivers the trace file.
    // Synchronous: result already available — callback runs inline, lock is still held (re-entrant)
    // Asynchronous: callback runs on an OS thread — acquires lock itself for restart
    currentProfiler.endAndCollect(
        traceFile ->
            onChunkCollected(
                traceFile,
                chunkProfilerId,
                chunkChunkId,
                chunkRecord,
                measurements,
                chunkTimestamp,
                shouldRestart,
                scopes,
                options));
  }

  private void onChunkCollected(
      final @Nullable File traceFile,
      final @NotNull SentryId chunkProfilerId,
      final @NotNull SentryId chunkChunkId,
      final @Nullable ChunkRecord chunkRecord,
      final @NotNull Map<String, ProfileMeasurement> measurements,
      final @NotNull SentryDate chunkTimestamp,
      final boolean shouldRestart,
      final @NotNull IScopes scopes,
      final @NotNull SentryOptions options) {
    // The trace file is the last word on whether the chunk was recorded: the OS may report success
    // and still leave no usable file behind
    if (chunkRecord != null) {
      // Nothing is sent once the profiler is closed, so a collected chunk still covers nothing
      chunkRecord.setRecordingState(
          traceFile != null && !isClosed.get()
              ? ProfileRecordingState.RECORDED
              : ProfileRecordingState.NOT_RECORDED);
    }

    if (traceFile == null) {
      logger.log(
          SentryLevel.ERROR,
          "An error occurred while collecting a profile chunk, and it won't be sent.");
    } else {
      final ProfileChunk.Builder builder =
          new ProfileChunk.Builder(
              chunkProfilerId,
              chunkChunkId,
              measurements,
              traceFile,
              chunkTimestamp,
              ProfileChunk.PLATFORM_ANDROID);
      builder.setContentType(ProfileChunk.CONTENT_TYPE_PERFETTO);
      sendChunk(builder, scopes, options);
    }

    if (shouldRestart) {
      try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
        // shouldStop is re-checked here (not just at capture time) because a stopProfiler() or
        // close() may have been requested while this async callback was pending.
        if (isRunning || isClosed.get() || shouldStop) {
          logger.log(
              SentryLevel.DEBUG,
              "Profile chunk finished, but profiler was already restarted, closed or stopped. Skipping.");
          return;
        }
        logger.log(SentryLevel.DEBUG, "Profile chunk finished. Starting a new one.");
        startInternal();
      }
    } else {
      logger.log(SentryLevel.DEBUG, "Profile chunk finished.");
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
    final @NotNull Runnable task =
        () -> {
          if (isClosed.get()) {
            return;
          }
          scopes.captureProfileChunk(builder.build(options));
        };
    try {
      // The chunk timer callback (stopInternal) already runs on the executor thread; submitting
      // back into the same single-threaded executor from there can deadlock, so run inline instead.
      if (Thread.currentThread().getName().startsWith(EXECUTOR_THREAD_NAME_PREFIX)) {
        task.run();
      } else {
        executorServiceSupplier.evaluate().submit(task);
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.DEBUG, "Failed to send profile chunk.", e);
    }
  }

  /**
   * Collects measurements for a single profiling chunk: frame metrics (slow/frozen frames, refresh
   * rate) and performance data (CPU usage, memory footprint).
   *
   * <p>Frame metrics are delivered on the FrameMetrics HandlerThread. The deques use {@link
   * ConcurrentLinkedDeque} because the HandlerThread writes and the executor thread reads.
   *
   * <p>Performance data is collected by the {@link CompositePerformanceCollector}'s Timer thread
   * every 100ms and returned as a list on {@code stop()}.
   */
  @VisibleForTesting
  static class ChunkMeasurementCollector {
    private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;
    private @Nullable String frameMetricsListenerId = null;
    private @Nullable CompositePerformanceCollector performanceCollector = null;
    private @Nullable String chunkId = null;

    private final @NotNull ConcurrentLinkedDeque<ProfileMeasurementValue>
        slowFrameRenderMeasurements = new ConcurrentLinkedDeque<>();
    private final @NotNull ConcurrentLinkedDeque<ProfileMeasurementValue>
        frozenFrameRenderMeasurements = new ConcurrentLinkedDeque<>();
    private final @NotNull ConcurrentLinkedDeque<ProfileMeasurementValue>
        screenFrameRateMeasurements = new ConcurrentLinkedDeque<>();

    // Elapsed realtime when the measurement was started (nanosecond precision).
    // Used to convert wall-time clock values into ns-since-chunk-start for the measurements
    // payload.
    private long profileStartElapsedRealtimeNanos = 0;

    ChunkMeasurementCollector(final @NotNull SentryFrameMetricsCollector frameMetricsCollector) {
      this.frameMetricsCollector = frameMetricsCollector;
    }

    void start(
        final @Nullable CompositePerformanceCollector performanceCollector,
        final @NotNull String chunkId) {
      this.performanceCollector = performanceCollector;
      this.chunkId = chunkId;
      this.profileStartElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();

      // Start frame metrics collection (runs on the FrameMetrics HandlerThread)
      slowFrameRenderMeasurements.clear();
      frozenFrameRenderMeasurements.clear();
      screenFrameRateMeasurements.clear();
      frameMetricsListenerId =
          frameMetricsCollector.startCollection(
              new SentryFrameMetricsCollector.FrameMetricsCollectorListener() {
                float lastRefreshRate = 0;

                @Override
                public void onFrameMetricCollected(
                    final long frameStartNanos,
                    final long frameEndNanos,
                    final long durationNanos,
                    final long delayNanos,
                    final boolean isSlow,
                    final boolean isFrozen,
                    final float refreshRate) {
                  final long timestampNanos = new SentryNanotimeDate().nanoTimestamp();
                  // Convert frameEndNanos (reported by FrameMetricsCollector using System.nanoTime
                  // /
                  // SystemClock.uptimeMillis), into the SystemClock.elapsedRealtime to report
                  // elapsed
                  // realtime nanos since chunk start
                  final long frameEndElapsedRealtimeNanos =
                      frameEndNanos - System.nanoTime() + SystemClock.elapsedRealtimeNanos();
                  final long frameTimestampRelativeNanos =
                      frameEndElapsedRealtimeNanos - profileStartElapsedRealtimeNanos;

                  // We don't allow negative relative timestamps, e.g. for a frame that started
                  // before the chunk did. This should never happen, but we check anyway.
                  if (frameTimestampRelativeNanos < 0) {
                    return;
                  }
                  if (isFrozen) {
                    frozenFrameRenderMeasurements.addLast(
                        new ProfileMeasurementValue(
                            frameTimestampRelativeNanos, durationNanos, timestampNanos));
                  } else if (isSlow) {
                    slowFrameRenderMeasurements.addLast(
                        new ProfileMeasurementValue(
                            frameTimestampRelativeNanos, durationNanos, timestampNanos));
                  }
                  if (refreshRate != lastRefreshRate) {
                    lastRefreshRate = refreshRate;
                    screenFrameRateMeasurements.addLast(
                        new ProfileMeasurementValue(
                            frameTimestampRelativeNanos, refreshRate, timestampNanos));
                  }
                }
              });

      // Start performance collection (runs on CompositePerformanceCollector's Timer thread)
      if (performanceCollector != null) {
        performanceCollector.start(chunkId);
      }
    }

    /**
     * Stops all collection, builds and returns the combined measurements map containing frame
     * metrics and performance data (CPU, memory).
     */
    @NotNull
    Map<String, ProfileMeasurement> stop() {
      final @NotNull Map<String, ProfileMeasurement> measurements = new HashMap<>();
      // Stop frame metrics
      frameMetricsCollector.stopCollection(frameMetricsListenerId);
      frameMetricsListenerId = null;
      addFrameDataToMeasurements(measurements);

      // Stop performance collection
      @Nullable List<PerformanceCollectionData> performanceData = null;
      if (performanceCollector != null && chunkId != null) {
        performanceData = performanceCollector.stop(chunkId);
        final long wallClockNowNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
        final long elapsedRealtimeNowNanos = SystemClock.elapsedRealtimeNanos();
        addPerformanceDataToMeasurements(
            performanceData,
            measurements,
            wallClockNowNanos,
            elapsedRealtimeNowNanos,
            profileStartElapsedRealtimeNanos);
      }
      performanceCollector = null;
      chunkId = null;

      return measurements;
    }

    private void addFrameDataToMeasurements(
        final @NotNull Map<String, ProfileMeasurement> measurements) {
      if (!slowFrameRenderMeasurements.isEmpty()) {
        measurements.put(
            ProfileMeasurement.ID_SLOW_FRAME_RENDERS,
            new ProfileMeasurement(
                ProfileMeasurement.UNIT_NANOSECONDS, new ArrayList<>(slowFrameRenderMeasurements)));
      }
      if (!frozenFrameRenderMeasurements.isEmpty()) {
        measurements.put(
            ProfileMeasurement.ID_FROZEN_FRAME_RENDERS,
            new ProfileMeasurement(
                ProfileMeasurement.UNIT_NANOSECONDS,
                new ArrayList<>(frozenFrameRenderMeasurements)));
      }
      if (!screenFrameRateMeasurements.isEmpty()) {
        measurements.put(
            ProfileMeasurement.ID_SCREEN_FRAME_RATES,
            new ProfileMeasurement(
                ProfileMeasurement.UNIT_HZ, new ArrayList<>(screenFrameRateMeasurements)));
      }
    }

    private static void addPerformanceDataToMeasurements(
        final @Nullable List<PerformanceCollectionData> performanceData,
        final @NotNull Map<String, ProfileMeasurement> measurements,
        final long wallClockNowNanos,
        final long elapsedRealtimeNowNanos,
        final long profileStartElapsedRealtimeNanos) {
      if (performanceData == null || performanceData.isEmpty()) {
        return;
      }
      final @NotNull ArrayDeque<ProfileMeasurementValue> cpuUsageMeasurements =
          new ArrayDeque<>(performanceData.size());
      final @NotNull ArrayDeque<ProfileMeasurementValue> memoryUsageMeasurements =
          new ArrayDeque<>(performanceData.size());
      final @NotNull ArrayDeque<ProfileMeasurementValue> nativeMemoryUsageMeasurements =
          new ArrayDeque<>(performanceData.size());

      // CompositePerformanceCollector.stop() hands back its live list, which its timer thread may
      // still write to, so we synchronize on it while iterating, as AndroidProfiler does.
      synchronized (performanceData) {
        for (final @NotNull PerformanceCollectionData data : performanceData) {
          // Convert sample timestamps (reported by CompositePerformanceCollector using
          // System.currentTimeMillis), into the SystemClock.elapsedRealtime to report
          // elapsed realtime nanos since chunk start
          final long nanoTimestamp = data.getNanoTimestamp();
          final long nanosSinceSample = wallClockNowNanos - nanoTimestamp;
          final long sampleElapsedRealtimeNanos = elapsedRealtimeNowNanos - nanosSinceSample;
          final long relativeStartNs =
              sampleElapsedRealtimeNanos - profileStartElapsedRealtimeNanos;
          if (data.hasCpuUsagePercentage()) {
            cpuUsageMeasurements.addLast(
                new ProfileMeasurementValue(
                    relativeStartNs, data.getCpuUsagePercentage(), nanoTimestamp));
          }
          if (data.hasUsedHeapMemory()) {
            memoryUsageMeasurements.addLast(
                new ProfileMeasurementValue(
                    relativeStartNs, data.getUsedHeapMemory(), nanoTimestamp));
          }
          if (data.hasUsedNativeMemory()) {
            nativeMemoryUsageMeasurements.addLast(
                new ProfileMeasurementValue(
                    relativeStartNs, data.getUsedNativeMemory(), nanoTimestamp));
          }
        }
      }

      if (!cpuUsageMeasurements.isEmpty()) {
        measurements.put(
            ProfileMeasurement.ID_CPU_USAGE,
            new ProfileMeasurement(ProfileMeasurement.UNIT_PERCENT, cpuUsageMeasurements));
      }
      if (!memoryUsageMeasurements.isEmpty()) {
        measurements.put(
            ProfileMeasurement.ID_MEMORY_FOOTPRINT,
            new ProfileMeasurement(ProfileMeasurement.UNIT_BYTES, memoryUsageMeasurements));
      }
      if (!nativeMemoryUsageMeasurements.isEmpty()) {
        measurements.put(
            ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT,
            new ProfileMeasurement(ProfileMeasurement.UNIT_BYTES, nativeMemoryUsageMeasurements));
      }
    }
  }
}
