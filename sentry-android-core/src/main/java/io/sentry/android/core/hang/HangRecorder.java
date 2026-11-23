package io.sentry.android.core.hang;

import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.AppState;
import io.sentry.android.core.AppStateProvider;
import io.sentry.time.EpochClock;
import io.sentry.time.MonotonicTicker;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.ExceptionUtils;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Opens a {@link HangInterval} when a {@link HangSignal} reports {@link
 * HangSignal.State#SUSPICIOUS}, lets the {@link HangDataCollector}s sample on each tick, and gives
 * a {@link HangRecord} to the subscribers when the interval ends.
 *
 * <p>The values of an interval stay in memory. If a subscriber asks for it in {@link
 * HangRecorderListener#onHangStarted(HangInterval)}, they also go to the {@link HangSampleStore}s
 * once the interval is {@code persistAfterMs} old, so most hangs cause no disk IO.
 *
 * <p>Runs on its own thread, {@code SentryHangRecorder}, which starts at the first subscription and
 * stops at {@link #close()}. All calls from other threads are moved to that thread, so the
 * recording state needs no lock. The recorder calls its signals, its collectors, its stores and the
 * tick listeners only on that thread.
 *
 * <p>Needs a monotonic ticker that does not advance during device sleep, so a device suspend does
 * not read as a hang.
 */
@ApiStatus.Internal
public final class HangRecorder implements Metronome, Closeable, AppState.AppStateListener {

  public static final long DEFAULT_POLLING_INTERVAL_MS = 66;
  public static final long DEFAULT_MAX_RECORDING_TIME_MS = 8_000;
  public static final long DEFAULT_PERSIST_AFTER_MS = 700;

  /**
   * Creates the executor of the recorder thread. It must run one task at a time on one thread, in
   * the order they were queued.
   */
  public interface ExecutorFactory {
    @NotNull
    ScheduledExecutorService create();
  }

  /** Creates the signals of a recorder, with the recorder as their metronome. */
  public interface SignalFactory {
    @NotNull
    List<HangSignal> create(@NotNull Metronome metronome);
  }

  /** A collector and its optional store. */
  public static final class CollectorSetup<T> {
    private final @NotNull HangDataCollector<T> collector;
    private final @Nullable HangSampleStore<T> store;

    public CollectorSetup(
        final @NotNull HangDataCollector<T> collector, final @Nullable HangSampleStore<T> store) {
      this.collector = collector;
      this.store = store;
    }
  }

  public static final @NotNull ExecutorFactory DEFAULT_EXECUTOR_FACTORY =
      () ->
          Executors.newSingleThreadScheduledExecutor(runnable -> {
            final @NotNull Thread thread = new Thread(runnable, "SentryHangRecorder");
            thread.setDaemon(true);
            return thread;
          });

  private final @NotNull SentryOptions options;
  private final @NotNull AppStateProvider appStateProvider;
  private final @NotNull MonotonicTicker ticker;
  private final @NotNull EpochClock epochClock;
  private final long pollingIntervalMs;
  private final long maxRecordingTimeMs;
  private final long persistAfterMs;
  private final int maxSamples;
  private final @NotNull ExecutorFactory executorFactory;
  private final @NotNull List<HangSignal> signals;
  private final @NotNull List<CollectorSetup<?>> collectors;
  private final @NotNull List<TickListener> tickListeners = new CopyOnWriteArrayList<>();

  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  private volatile @Nullable ScheduledExecutorService executor;
  private volatile @Nullable Thread executorThread;

  private boolean closed;

  private final @NotNull Set<HangRecorderListener> subscriptions = new LinkedHashSet<>();
  private boolean signalsStarted;
  private @Nullable Future<?> tickFuture;
  private @Nullable HangInterval openInterval;
  private long openIntervalStartTimestampMs;
  private boolean persistOpenInterval;
  private @NotNull List<Recording<?>> recordings = Collections.emptyList();
  private boolean waitingForIdleAfterMaxRecordingTime;

  private final @NotNull HangSignalListener signalListener =
      new HangSignalListener() {
        @Override
        public void onSignalChanged(
            final @NotNull HangSignal signal,
            final @NotNull HangSignal.State state,
            final long durationMs) {
          final long sinceMs = nowMs() - durationMs;
          postOnRecorderThread(() -> handleSignalChanged(state, sinceMs));
        }

        @Override
        public void onSignalCanceled(final @NotNull HangSignal signal) {
          postOnRecorderThread(HangRecorder.this::handleSignalCanceled);
        }
      };

  /**
   * Creates a recorder that starts no thread before the first {@link
   * #subscribe(HangRecorderListener)}.
   */
  public HangRecorder(
      final @NotNull SentryOptions options,
      final @NotNull AppStateProvider appStateProvider,
      final @NotNull MonotonicTicker ticker,
      final @NotNull EpochClock epochClock,
      final long pollingIntervalMs,
      final long maxRecordingTimeMs,
      final long persistAfterMs,
      final int maxSamples,
      final @NotNull ExecutorFactory executorFactory,
      final @NotNull SignalFactory signalFactory,
      final @NotNull List<CollectorSetup<?>> collectors) {
    this.options = options;
    this.appStateProvider = appStateProvider;
    this.ticker = ticker;
    this.epochClock = epochClock;
    this.pollingIntervalMs = pollingIntervalMs;
    this.maxRecordingTimeMs = maxRecordingTimeMs;
    this.persistAfterMs = persistAfterMs;
    this.maxSamples = maxSamples;
    this.executorFactory = executorFactory;
    this.signals = Collections.unmodifiableList(new ArrayList<>(signalFactory.create(this)));
    this.collectors = Collections.unmodifiableList(new ArrayList<>(collectors));
  }

  /**
   * Starts the recorder thread at the first call, and the signals when the app is in the
   * foreground. The recorder calls {@link HangRecorderListener#onHangStarted(HangInterval)} on the
   * recorder thread and {@link HangRecorderListener#onHangRecorded(HangRecord)} on the SDK
   * executor, see {@link SentryOptions#getExecutorService()}. Does nothing after {@link #close()}.
   */
  public void subscribe(final @NotNull HangRecorderListener listener) {
    postOnRecorderThread(
        () -> {
          if (subscriptions.add(listener) && subscriptions.size() == 1) {
            appStateProvider.addAppStateListener(this);
          }
        },
        true);
  }

  /**
   * At the last subscription, stops the signals and the tick, and drops an open interval without a
   * record. The recorder thread stays for the next {@link #subscribe(HangRecorderListener)}.
   */
  public void unsubscribe(final @NotNull HangRecorderListener listener) {
    postOnRecorderThread(
        () -> {
          if (subscriptions.remove(listener) && subscriptions.isEmpty()) {
            appStateProvider.removeAppStateListener(this);
            stopSignals(false);
          }
        },
        false);
  }

  /**
   * The recorder calls the listener on the recorder thread, on each tick before the collectors
   * sample.
   */
  @Override
  public void addTickListener(final @NotNull TickListener listener) {
    tickListeners.add(listener);
  }

  @Override
  public void removeTickListener(final @NotNull TickListener listener) {
    tickListeners.remove(listener);
  }

  /**
   * Drops an open interval without a record, closes the signals, the collectors and the stores, and
   * stops the recorder thread. Later calls do nothing.
   */
  @Override
  public void close() {
    final @Nullable ScheduledExecutorService current;
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (closed) {
        return;
      }
      closed = true;
      current = executor;
      if (current != null) {
        current.execute(() -> closeOnRecorderThread(current));
      }
    }
    if (current == null) {
      // No recorder thread was started, so close here.
      closeSignalsAndCollectors();
    }
  }

  @Override
  public void onForeground() {
    postOnRecorderThread(this::startSignals, false);
  }

  @Override
  public void onBackground() {
    postOnRecorderThread(() -> stopSignals(true), false);
  }

  private void postOnRecorderThread(
      final @NotNull Runnable task, final boolean startRecorderThread) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (closed) {
        return;
      }
      @Nullable ScheduledExecutorService current = executor;
      if (current == null) {
        if (!startRecorderThread) {
          return;
        }
        current = executorFactory.create();
        executor = current;
        // Runs before any other task of this single-thread executor.
        current.execute(() -> executorThread = Thread.currentThread());
      }
      current.execute(task);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void postOnRecorderThread(final @NotNull Runnable task) {
    if (Thread.currentThread() == executorThread) {
      task.run();
    } else {
      postOnRecorderThread(task, false);
    }
  }

  private void closeOnRecorderThread(final @NotNull ScheduledExecutorService self) {
    try {
      try {
        if (!subscriptions.isEmpty()) {
          appStateProvider.removeAppStateListener(this);
          stopSignals(false);
        }
      } finally {
        subscriptions.clear();
        closeSignalsAndCollectors();
      }
    } finally {
      self.shutdown();
    }
  }

  private void closeSignalsAndCollectors() {
    try {
      for (final @NotNull HangSignal signal : signals) {
        signal.close();
      }
    } finally {
      for (final @NotNull CollectorSetup<?> setup : collectors) {
        try {
          setup.collector.close();
        } catch (IOException e) {
          options.getLogger().log(SentryLevel.WARNING, "Failed to close a hang data collector", e);
        }
        final @Nullable HangSampleStore<?> store = setup.store;
        if (store != null) {
          try {
            store.close();
          } catch (IOException e) {
            options.getLogger().log(SentryLevel.WARNING, "Failed to close a hang sample store", e);
          }
        }
      }
    }
  }

  private void startSignals() {
    final @Nullable ScheduledExecutorService self = executor;
    if (self == null || subscriptions.isEmpty() || signalsStarted) {
      return;
    }
    signalsStarted = true;
    for (final @NotNull HangSignal signal : signals) {
      signal.start(signalListener);
    }
    tickFuture =
        self.scheduleWithFixedDelay(this::tick, 0, pollingIntervalMs, TimeUnit.MILLISECONDS);
  }

  private void stopSignals(final boolean endIntervalWithRecord) {
    if (!signalsStarted) {
      return;
    }
    signalsStarted = false;
    waitingForIdleAfterMaxRecordingTime = false;
    final @Nullable Future<?> future = tickFuture;
    if (future != null) {
      future.cancel(false);
      tickFuture = null;
    }
    try {
      for (final @NotNull HangSignal signal : signals) {
        signal.stop();
      }
    } finally {
      if (endIntervalWithRecord) {
        endInterval(nowMs(), false);
      } else {
        cancelInterval();
      }
    }
  }

  private void tick() {
    try {
      final long nowMs = nowMs();
      for (final @NotNull TickListener listener : tickListeners) {
        listener.onMetronomeTick(nowMs);
      }
      final @Nullable HangInterval interval = openInterval;
      if (interval == null) {
        return;
      }
      try {
        for (final @NotNull Recording<?> recording : recordings) {
          sample(recording, interval, nowMs);
        }
      } finally {
        // A collector that throws on every tick must not keep the interval open past the maximum.
        if (nowMs - interval.getStartTimeMs() >= maxRecordingTimeMs) {
          endInterval(interval.getStartTimeMs() + maxRecordingTimeMs, true);
          waitingForIdleAfterMaxRecordingTime = true;
        }
      }
    } catch (Throwable t) {
      // scheduleWithFixedDelay stops all later ticks after an exception.
      ExceptionUtils.rethrowIfFatal(t);
      options.getLogger().log(SentryLevel.ERROR, "HangRecorder tick failed", t);
    }
  }

  private void handleSignalChanged(final @NotNull HangSignal.State state, final long sinceMs) {
    if (!signalsStarted) {
      return;
    }
    if (state == HangSignal.State.SUSPICIOUS) {
      if (openInterval != null || waitingForIdleAfterMaxRecordingTime) {
        return;
      }
      final long nowTimestampMs = TimeUnit.NANOSECONDS.toMillis(epochClock.now().epochNanos());
      final @NotNull HangInterval interval = new HangInterval(sinceMs);
      boolean persist = false;
      for (final @NotNull HangRecorderListener listener : subscriptions) {
        if (listener.onHangStarted(interval).shouldPersist()) {
          persist = true;
        }
      }
      final @NotNull List<Recording<?>> intervalRecordings = new ArrayList<>(collectors.size());
      for (final @NotNull CollectorSetup<?> setup : collectors) {
        intervalRecordings.add(new Recording<>(setup));
      }
      openInterval = interval;
      openIntervalStartTimestampMs = nowTimestampMs - (nowMs() - sinceMs);
      persistOpenInterval = persist;
      recordings = intervalRecordings;
    } else {
      waitingForIdleAfterMaxRecordingTime = false;
      endInterval(sinceMs, false);
    }
  }

  private void handleSignalCanceled() {
    waitingForIdleAfterMaxRecordingTime = false;
    cancelInterval();
  }

  private void cancelInterval() {
    openInterval = null;
    recordings = Collections.emptyList();
  }

  private void endInterval(final long endTimeMs, final boolean endedAtMaxRecordingTime) {
    final @Nullable HangInterval interval = openInterval;
    if (interval == null) {
      return;
    }
    final @NotNull List<Recording<?>> intervalRecordings = recordings;
    openInterval = null;
    recordings = Collections.emptyList();
    final @NotNull HangRecord.Builder builder =
        new HangRecord.Builder(endTimeMs - interval.getStartTimeMs(), openIntervalStartTimestampMs)
            .setEndedAtMaxRecordingTime(endedAtMaxRecordingTime);
    for (final @NotNull Recording<?> recording : intervalRecordings) {
      recording.stop(builder);
    }
    final @NotNull HangRecord record = builder.build();
    final @NotNull List<HangRecorderListener> listeners = new ArrayList<>(subscriptions);
    try {
      final @NotNull Future<?> delivery =
          options
              .getExecutorService()
              .submit(() -> {
                for (final @NotNull HangRecorderListener listener : listeners) {
                  listener.onHangRecorded(record);
                }
              });
      // The SDK executor returns a canceled future instead of throwing when its queue is full.
      if (delivery.isCancelled()) {
        options
            .getLogger()
            .log(SentryLevel.WARNING, "Dropped a hang record, the SDK executor queue is full");
      }
    } catch (RejectedExecutionException e) {
      options.getLogger().log(SentryLevel.WARNING, "Dropped a hang record, executor is closed", e);
    }
  }

  private <T> void sample(
      final @NotNull Recording<T> recording,
      final @NotNull HangInterval interval,
      final long nowMs) {
    if (recording.samples.size() >= maxSamples) {
      return;
    }
    final @Nullable T value = recording.setup.collector.onSample(interval);
    if (value != null) {
      recording.samples.add(value);
    }
    final @Nullable HangSampleStore<T> store = recording.setup.store;
    if (!persistOpenInterval
        || store == null
        || recording.storeFailed
        || nowMs - interval.getStartTimeMs() < persistAfterMs) {
      return;
    }
    try {
      if (!recording.persisting) {
        recording.persisting = true;
        store.clear();
        for (final @NotNull T buffered : recording.samples) {
          store.add(buffered);
        }
      } else if (value != null) {
        store.add(value);
      }
    } catch (IOException e) {
      recording.storeFailed = true;
      options.getLogger().log(SentryLevel.WARNING, "Failed to write to a hang sample store", e);
    }
  }

  private long nowMs() {
    return TimeUnit.NANOSECONDS.toMillis(ticker.tickNanos());
  }

  /** The values of one collector in the open interval. */
  private static final class Recording<T> {
    private final @NotNull CollectorSetup<T> setup;
    private final @NotNull List<T> samples = new ArrayList<>();
    private boolean persisting;
    private boolean storeFailed;

    private Recording(final @NotNull CollectorSetup<T> setup) {
      this.setup = setup;
    }

    private void stop(final @NotNull HangRecord.Builder out) {
      setup.collector.onRecordingStopped(new ArrayList<>(samples), out);
    }
  }
}
