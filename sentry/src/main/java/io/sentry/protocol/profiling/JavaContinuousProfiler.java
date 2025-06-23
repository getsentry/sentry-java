package io.sentry.protocol.profiling;

import static io.sentry.DataCategory.All;
import static io.sentry.IConnectionStatusProvider.ConnectionStatus.DISCONNECTED;

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
import io.sentry.SentryUUID;
import io.sentry.TracesSampler;
import io.sentry.protocol.SentryId;
import io.sentry.transport.RateLimiter;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.SentryRandom;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import one.profiler.AsyncProfiler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class JavaContinuousProfiler
    implements IContinuousProfiler, RateLimiter.IRateLimitObserver {
  private static final long MAX_CHUNK_DURATION_MILLIS = 10000;

  private final @NotNull ILogger logger;
  private final @Nullable String profilingTracesDirPath;
  private final int profilingTracesHz;
  private final @NotNull ISentryExecutorService executorService;
  private boolean isInitialized = false;
  private boolean isRunning = false;
  private @Nullable IScopes scopes;
  private @Nullable Future<?> stopFuture;
  private final @NotNull List<ProfileChunk.Builder> payloadBuilders = new ArrayList<>();
  private @NotNull SentryId profilerId = SentryId.EMPTY_ID;
  private @NotNull SentryId chunkId = SentryId.EMPTY_ID;
  private final @NotNull AtomicBoolean isClosed = new AtomicBoolean(false);
  private @NotNull SentryDate startProfileChunkTimestamp = new SentryNanotimeDate();

  private @NotNull String filename = "";

  private final @NotNull AsyncProfiler profiler;
  private volatile boolean shouldSample = true;
  private boolean shouldStop = false;
  private boolean isSampled = false;
  private int rootSpanCounter = 0;

  private final AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  private final AutoClosableReentrantLock payloadLock = new AutoClosableReentrantLock();

  public JavaContinuousProfiler(
      final @NotNull ILogger logger,
      final @Nullable String profilingTracesDirPath,
      final int profilingTracesHz,
      final @NotNull ISentryExecutorService executorService) {
    this.logger = logger;
    this.profilingTracesDirPath = profilingTracesDirPath;
    this.profilingTracesHz = profilingTracesHz;
    this.executorService = executorService;
    this.profiler = AsyncProfiler.getInstance();
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

    //    profiler =
    //      new AndroidProfiler(
    //        profilingTracesDirPath,
    //        (int) SECONDS.toMicros(1) / profilingTracesHz,
    //        frameMetricsCollector,
    //        null,
    //        logger);
  }

  @SuppressWarnings("ReferenceEquality")
  @Override
  public void startProfiler(
      final @NotNull ProfileLifecycle profileLifecycle,
      final @NotNull TracesSampler tracesSampler) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (shouldSample) {
        isSampled = tracesSampler.sampleSessionProfile(SentryRandom.current().nextDouble());
        // Kepp TRUE for now
        //        shouldSample = false;
      }
      if (!isSampled) {
        logger.log(SentryLevel.DEBUG, "Profiler was not started due to sampling decision.");
        return;
      }

      if (!isRunning()) {
        logger.log(SentryLevel.DEBUG, "Started Profiler.");
        start();
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void start() {
    if ((scopes == null || scopes == NoOpScopes.getInstance())
        && Sentry.getCurrentScopes() != NoOpScopes.getInstance()) {
      this.scopes = Sentry.forkedRootScopes("profiler");
      final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
      if (rateLimiter != null) {
        rateLimiter.addRateLimitObserver(this);
      }
    }

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
    filename = SentryUUID.generateSentryId() + ".jfr";
    final String startData;
    try {
      //      System.out.println("### Starting profiler with start,jfr,event=wall,file");
      startData = profiler.execute("start,jfr,event=cpu,alloc,file=" + filename);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    // check if profiling started
    if (startData == null) {
      return;
    }

    isRunning = true;

    if (SentryId.EMPTY_ID.equals(profilerId)) {
      profilerId = new SentryId();
    }

    if (chunkId == SentryId.EMPTY_ID) {
      chunkId = new SentryId();
    }

    try {
      stopFuture = executorService.schedule(() -> stop(true), MAX_CHUNK_DURATION_MILLIS);
    } catch (RejectedExecutionException e) {
      logger.log(
          SentryLevel.ERROR,
          "Failed to schedule profiling chunk finish. Did you call Sentry.close()?",
          e);
      shouldStop = true;
    }
  }

  @Override
  public void stopProfiler(final @NotNull ProfileLifecycle profileLifecycle) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      shouldStop = true;
    }
  }

  private void stop(final boolean restartProfiler) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (stopFuture != null) {
        stopFuture.cancel(true);
      }
      // check if profiler was created and it's running
      if (profiler == null || !isRunning) {
        // When the profiler is stopped due to an error (e.g. offline or rate limited), reset the
        // ids
        profilerId = SentryId.EMPTY_ID;
        chunkId = SentryId.EMPTY_ID;
        return;
      }

      String endData = null;
      try {
        endData = profiler.execute("stop,jfr");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      // check if profiler end successfully
      if (endData == null) {
        logger.log(
            SentryLevel.ERROR,
            "An error occurred while collecting a profile chunk, and it won't be sent.");
      } else {
        // The scopes can be null if the profiler is started before the SDK is initialized (app
        // start profiling), meaning there's no scopes to send the chunks. In that case, we store
        // the data in a list and send it when the next chunk is finished.
        try (final @NotNull ISentryLifecycleToken ignored2 = payloadLock.acquire()) {
          payloadBuilders.add(
              new ProfileChunk.Builder(
                  profilerId,
                  chunkId,
                  new HashMap<>(),
                  new File(filename),
                  startProfileChunkTimestamp));
        }
      }

      isRunning = false;
      // A chunk is finished. Next chunk will have a different id.
      chunkId = SentryId.EMPTY_ID;
      filename = "";

      if (scopes != null) {
        sendChunks(scopes, scopes.getOptions());
      }

      if (restartProfiler && !shouldStop) {
        logger.log(SentryLevel.DEBUG, "Profile chunk finished. Starting a new one.");
        start();
      } else {
        // When the profiler is stopped manually, we have to reset its id
        profilerId = SentryId.EMPTY_ID;
        logger.log(SentryLevel.DEBUG, "Profile chunk finished.");
      }
    }
  }

  @Override
  public void reevaluateSampling() {
    shouldSample = true;
  }

  @Override
  public void close(final boolean isTerminating) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      rootSpanCounter = 0;
      shouldStop = true;
      if (isTerminating) {
        stop(false);
        isClosed.set(true);
      }
    }
  }

  @Override
  public @NotNull SentryId getProfilerId() {
    return profilerId;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
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
    //    if (rateLimiter.isActiveForCategory(All)
    //      || rateLimiter.isActiveForCategory(DataCategory.ProfileChunk)) {
    //      logger.log(SentryLevel.WARNING, "SDK is rate limited. Stopping profiler.");
    //      stop(false);
    //    }
    // If we are not rate limited anymore, we don't do anything: the profile is broken, so it's
    // useless to restart it automatically
  }
}
