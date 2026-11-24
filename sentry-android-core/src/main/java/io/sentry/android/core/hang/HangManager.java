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
 * Starts a {@link HangRecorder} when a {@link HangSignal} reports {@link
 * HangSignal.State#SUSPICIOUS}, lets it sample the {@link HangDataCollector}s on each tick, and
 * gives its {@link HangRecord} to the subscribers that asked for it when the hang ends.
 *
 * <p>Each subscriber says in {@link HangRecordingListener#onHangStarted()} what it needs from the
 * hang. If no subscriber needs a record, the hang is not recorded and the collectors are not
 * called. If a subscriber asks for it, the values also go to the {@link HangSampleStore}s from the
 * start of the hang, so the fatal ANR path can read them after a process kill.
 *
 * <p>Runs on its own thread, {@code SentryHangManager}, which starts at the first subscription and
 * stops at the last {@link #unsubscribe(HangRecordingListener)}. The manager is then closed, and
 * later calls do nothing. All calls from other threads are moved to that thread, so the recording
 * state needs no lock. The manager calls its signals, its collectors, its stores and the tick
 * listeners only on that thread.
 *
 * <p>Needs a monotonic ticker that does not advance during device sleep, so a device suspend does
 * not read as a hang.
 */
@ApiStatus.Internal
public final class HangManager implements Metronome, AppState.AppStateListener {

  public static final long DEFAULT_POLLING_INTERVAL_MS = 66;
  public static final long DEFAULT_MAX_RECORDING_TIME_MS = 8_000;
  public static final long DEFAULT_SUSPICIOUS_THRESHOLD_MS = 300;

  /**
   * Creates the executor of the manager thread. It must run one task at a time on one thread, in
   * the order they were queued.
   */
  public interface ExecutorFactory {
    @NotNull
    ScheduledExecutorService create();
  }

  /** Creates the signals of a manager, with the manager as their metronome. */
  public interface SignalFactory {
    @NotNull
    List<HangSignal> create(@NotNull Metronome metronome);
  }

  public static final @NotNull ExecutorFactory DEFAULT_EXECUTOR_FACTORY =
      () ->
          Executors.newSingleThreadScheduledExecutor(runnable -> {
            final @NotNull Thread thread = new Thread(runnable, "SentryHangManager");
            thread.setDaemon(true);
            return thread;
          });

  private final @NotNull SentryOptions options;
  private final @NotNull AppStateProvider appStateProvider;
  private final @NotNull MonotonicTicker ticker;
  private final @NotNull EpochClock epochClock;
  private final long pollingIntervalMs;
  private final long maxRecordingTimeMs;
  private final int maxSamples;
  private final @NotNull ExecutorFactory executorFactory;
  private final @NotNull List<HangSignal> signals;
  private final @NotNull List<HangCollectorSetup<?>> collectors;
  private final @NotNull List<TickListener> tickListeners = new CopyOnWriteArrayList<>();

  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  private volatile @Nullable ScheduledExecutorService executor;
  private volatile @Nullable Thread executorThread;

  private volatile boolean closed;

  private final @NotNull Set<HangRecordingListener> subscriptions = new LinkedHashSet<>();
  private boolean signalsStarted;
  private @Nullable Future<?> tickFuture;
  private @Nullable HangRecorder activeRecorder;
  private boolean waitingForIdleAfterMaxRecordingTime;

  private final @NotNull HangSignalListener signalListener =
      new HangSignalListener() {
        @Override
        public void onSignalChanged(
            final @NotNull HangSignal signal,
            final @NotNull HangSignal.State state,
            final long durationMs) {
          final long sinceMs = nowMs() - durationMs;
          postOnManagerThread(() -> handleSignalChanged(state, sinceMs));
        }

        @Override
        public void onSignalCanceled(final @NotNull HangSignal signal) {
          postOnManagerThread(HangManager.this::handleSignalCanceled);
        }
      };

  /**
   * Creates a manager that starts no thread before the first {@link
   * #subscribe(HangRecordingListener)}.
   */
  public HangManager(
      final @NotNull SentryOptions options,
      final @NotNull AppStateProvider appStateProvider,
      final @NotNull MonotonicTicker ticker,
      final @NotNull EpochClock epochClock,
      final long pollingIntervalMs,
      final long maxRecordingTimeMs,
      final int maxSamples,
      final @NotNull ExecutorFactory executorFactory,
      final @NotNull SignalFactory signalFactory,
      final @NotNull List<HangCollectorSetup<?>> collectors) {
    this.options = options;
    this.appStateProvider = appStateProvider;
    this.ticker = ticker;
    this.epochClock = epochClock;
    this.pollingIntervalMs = pollingIntervalMs;
    this.maxRecordingTimeMs = maxRecordingTimeMs;
    this.maxSamples = maxSamples;
    this.executorFactory = executorFactory;
    this.signals = Collections.unmodifiableList(new ArrayList<>(signalFactory.create(this)));
    this.collectors = Collections.unmodifiableList(new ArrayList<>(collectors));
  }

  /**
   * Starts the manager thread at the first call, and the signals when the app is in the foreground.
   * The manager calls {@link HangRecordingListener#onHangStarted()} on the manager thread and
   * {@link HangRecordingListener#onHangRecorded(HangRecord)} on the SDK executor, see {@link
   * SentryOptions#getExecutorService()}. A listener gets the record of a hang only if it asked for
   * a record when the hang started. Does nothing after the last {@link
   * #unsubscribe(HangRecordingListener)}.
   */
  public void subscribe(final @NotNull HangRecordingListener listener) {
    postOnManagerThread(
        () -> {
          if (subscriptions.add(listener) && subscriptions.size() == 1) {
            appStateProvider.addAppStateListener(this);
          }
        },
        true);
  }

  /**
   * The last unsubscribe closes the manager: it stops the signals and the tick, drops an open hang
   * record without delivering it, closes the signals and the stores, and stops the manager thread.
   * Later calls do nothing. Otherwise the listener still gets the record of an open hang that it
   * asked for.
   */
  public void unsubscribe(final @NotNull HangRecordingListener listener) {
    postOnManagerThread(
        () -> {
          if (subscriptions.remove(listener) && subscriptions.isEmpty()) {
            closeOnManagerThread();
          }
        },
        false);
  }

  /**
   * The manager calls the listener on the manager thread, on each tick before the collectors
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

  @Override
  public void onForeground() {
    postOnManagerThread(this::startSignals, false);
  }

  @Override
  public void onBackground() {
    postOnManagerThread(() -> stopSignals(true), false);
  }

  private void postOnManagerThread(final @NotNull Runnable task, final boolean startManagerThread) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (closed) {
        return;
      }
      @Nullable ScheduledExecutorService current = executor;
      if (current == null) {
        if (!startManagerThread) {
          return;
        }
        current = executorFactory.create();
        executor = current;
        // Runs before any other task of this single-thread executor.
        current.execute(() -> executorThread = Thread.currentThread());
      }
      current.execute(() -> {
        // A task queued before the last unsubscribe ran must not open the manager again.
        if (!closed) {
          task.run();
        }
      });
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void postOnManagerThread(final @NotNull Runnable task) {
    if (Thread.currentThread() == executorThread) {
      task.run();
    } else {
      postOnManagerThread(task, false);
    }
  }

  private void closeOnManagerThread() {
    final @Nullable ScheduledExecutorService self;
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      closed = true;
      self = executor;
    }
    try {
      try {
        appStateProvider.removeAppStateListener(this);
        stopSignals(false);
      } finally {
        closeSignalsAndStores();
      }
    } finally {
      if (self != null) {
        self.shutdown();
      }
    }
  }

  private void closeSignalsAndStores() {
    try {
      for (final @NotNull HangSignal signal : signals) {
        signal.close();
      }
    } finally {
      for (final @NotNull HangCollectorSetup<?> setup : collectors) {
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
    // Scheduled first, so a signal that fails to start does not stop the tick for the others.
    tickFuture =
        self.scheduleWithFixedDelay(this::tick, 0, pollingIntervalMs, TimeUnit.MILLISECONDS);
    for (final @NotNull HangSignal signal : signals) {
      signal.start(signalListener);
    }
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
        endRecord(nowMs(), false);
      } else {
        cancelRecord();
      }
    }
  }

  private void tick() {
    try {
      final long nowMs = nowMs();
      try {
        for (final @NotNull TickListener listener : tickListeners) {
          listener.onMetronomeTick(nowMs);
        }
        final @Nullable HangRecorder recorder = activeRecorder;
        if (recorder != null) {
          recorder.sample(maxSamples);
        }
      } finally {
        // A tick listener or a collector that throws on every tick must not keep the hang open
        // past the maximum.
        final @Nullable HangRecorder recorder = activeRecorder;
        if (recorder != null && nowMs - recorder.getStartTimeMs() >= maxRecordingTimeMs) {
          endRecord(recorder.getStartTimeMs() + maxRecordingTimeMs, true);
          waitingForIdleAfterMaxRecordingTime = true;
        }
      }
    } catch (Throwable t) {
      // scheduleWithFixedDelay stops all later ticks after an exception.
      ExceptionUtils.rethrowIfFatal(t);
      options.getLogger().log(SentryLevel.ERROR, "HangManager tick failed", t);
    }
  }

  private void handleSignalChanged(final @NotNull HangSignal.State state, final long sinceMs) {
    if (!signalsStarted) {
      return;
    }
    if (state == HangSignal.State.SUSPICIOUS) {
      if (activeRecorder != null || waitingForIdleAfterMaxRecordingTime) {
        return;
      }
      final long nowTimestampMs = TimeUnit.NANOSECONDS.toMillis(epochClock.now().epochNanos());
      activeRecorder =
          HangRecorder.start(
              subscriptions,
              collectors,
              sinceMs,
              nowTimestampMs - (nowMs() - sinceMs),
              options.getLogger());
    } else {
      waitingForIdleAfterMaxRecordingTime = false;
      endRecord(sinceMs, false);
    }
  }

  private void handleSignalCanceled() {
    waitingForIdleAfterMaxRecordingTime = false;
    cancelRecord();
  }

  private void cancelRecord() {
    activeRecorder = null;
  }

  private void endRecord(final long endTimeMs, final boolean endedAtMaxRecordingTime) {
    final @Nullable HangRecorder recorder = activeRecorder;
    if (recorder == null) {
      return;
    }
    activeRecorder = null;
    final @NotNull HangRecord record = recorder.end(endTimeMs, endedAtMaxRecordingTime);
    final @NotNull List<HangRecordingListener> listeners = recorder.getListeners();
    try {
      final @NotNull Future<?> delivery =
          options
              .getExecutorService()
              .submit(() -> {
                for (final @NotNull HangRecordingListener listener : listeners) {
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

  @Override
  public long nowMs() {
    return TimeUnit.NANOSECONDS.toMillis(ticker.tickNanos());
  }
}
