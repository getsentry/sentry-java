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
 * Starts a {@link HangRecord} when a {@link HangSignal} reports {@link
 * HangSignal.State#SUSPICIOUS}, lets the {@link HangDataCollector}s sample into it on each tick,
 * and gives it to the subscribers that asked for it when the hang ends.
 *
 * <p>Each subscriber says in {@link HangRecorderListener#onHangStarted()} what it needs from the
 * hang. If no subscriber needs a record, the hang is not recorded and the collectors are not
 * called. If a subscriber asks for it, the values also go to the {@link HangSampleStore}s from the
 * start of the hang, so the fatal ANR path can read them after a process kill.
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

  // Not a BiConsumer, which needs API 24.
  /** Puts one value of a collector into the record. */
  public interface RecordWriter<T> {
    void write(@NotNull T sample, @NotNull HangRecord.Builder record);
  }

  /** A collector, the writer of its values into the record, and its optional store. */
  public static final class CollectorSetup<T> {
    private final @NotNull HangDataCollector<T> collector;
    private final @NotNull RecordWriter<T> writer;
    private final @Nullable HangSampleStore<T> store;

    public CollectorSetup(
        final @NotNull HangDataCollector<T> collector,
        final @NotNull RecordWriter<T> writer,
        final @Nullable HangSampleStore<T> store) {
      this.collector = collector;
      this.writer = writer;
      this.store = store;
    }

    public void sample(final @NotNull HangRecord.Builder record) throws IOException {
      final @Nullable T sample = collector.sample();
      if (sample != null) {
        writer.write(sample, record);
        if (store != null && record.isShouldPersist()) {
          store.add(sample);
        }
      }
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
  private @Nullable HangRecord.Builder openHangRecord;
  private @NotNull List<HangRecorderListener> recordingSubscribers = Collections.emptyList();
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
    this.maxSamples = maxSamples;
    this.executorFactory = executorFactory;
    this.signals = Collections.unmodifiableList(new ArrayList<>(signalFactory.create(this)));
    this.collectors = Collections.unmodifiableList(new ArrayList<>(collectors));
  }

  /**
   * Starts the recorder thread at the first call, and the signals when the app is in the
   * foreground. The recorder calls {@link HangRecorderListener#onHangStarted()} on the recorder
   * thread and {@link HangRecorderListener#onHangRecorded(HangRecord)} on the SDK executor, see
   * {@link SentryOptions#getExecutorService()}. Does nothing after {@link #close()}.
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
   * At the last subscription, stops the signals and the tick, and drops an open hang record without
   * delivering it. The recorder thread stays for the next {@link #subscribe(HangRecorderListener)}.
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
   * Drops an open hang record without delivering it, closes the signals and the stores, and stops
   * the recorder thread. Later calls do nothing.
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
      closeSignalsAndStores();
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
        closeSignalsAndStores();
      }
    } finally {
      self.shutdown();
    }
  }

  private void closeSignalsAndStores() {
    try {
      for (final @NotNull HangSignal signal : signals) {
        signal.close();
      }
    } finally {
      for (final @NotNull CollectorSetup<?> setup : collectors) {
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
        endRecord(nowMs(), false);
      } else {
        cancelRecord();
      }
    }
  }

  private void tick() {
    try {
      final long nowMs = nowMs();
      for (final @NotNull TickListener listener : tickListeners) {
        listener.onMetronomeTick(nowMs);
      }
      final @Nullable HangRecord.Builder hangRecord = openHangRecord;
      if (hangRecord == null) {
        return;
      }
      try {
        // Stop sampling at the cap instead of rotating out samples: the early ones show how the
        // hang began.
        if (hangRecord.increaseSamplingCount() <= maxSamples) {
          for (final @NotNull CollectorSetup<?> setup : collectors) {
            try {
              setup.sample(hangRecord);
            } catch (IOException e) {
              stopPersisting(hangRecord, e);
            }
          }
        }
      } finally {
        // A collector that throws on every tick must not keep the hang open past the maximum.
        if (nowMs - hangRecord.getStartTimeMs() >= maxRecordingTimeMs) {
          endRecord(hangRecord.getStartTimeMs() + maxRecordingTimeMs, true);
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
      if (openHangRecord != null || waitingForIdleAfterMaxRecordingTime) {
        return;
      }
      final long nowTimestampMs = TimeUnit.NANOSECONDS.toMillis(epochClock.now().epochNanos());
      boolean persist = false;
      final @NotNull List<HangRecorderListener> hangSubscribers = new ArrayList<>();
      for (final @NotNull HangRecorderListener listener : subscriptions) {
        final @Nullable HangRecordingRequest request = listener.onHangStarted();
        if (request != null && request.shouldRecord()) {
          hangSubscribers.add(listener);
          if (request.shouldPersist()) {
            persist = true;
          }
        }
      }
      if (hangSubscribers.isEmpty()) {
        return;
      }
      final @NotNull HangRecord.Builder hangRecord =
          new HangRecord.Builder()
              .setStartTimeMs(sinceMs)
              .setStartTimestampMs(nowTimestampMs - (nowMs() - sinceMs))
              .setShouldPersist(persist);
      if (persist) {
        clearStores(hangRecord);
      }
      openHangRecord = hangRecord;
      recordingSubscribers = hangSubscribers;
    } else {
      waitingForIdleAfterMaxRecordingTime = false;
      endRecord(sinceMs, false);
    }
  }

  private void handleSignalCanceled() {
    waitingForIdleAfterMaxRecordingTime = false;
    cancelRecord();
  }

  private void clearStores(final @NotNull HangRecord.Builder hangRecord) {
    for (final @NotNull CollectorSetup<?> setup : collectors) {
      final @Nullable HangSampleStore<?> store = setup.store;
      if (store == null) {
        continue;
      }
      try {
        store.clear();
      } catch (IOException e) {
        stopPersisting(hangRecord, e);
        return;
      }
    }
  }

  private void stopPersisting(
      final @NotNull HangRecord.Builder hangRecord, final @NotNull IOException e) {
    hangRecord.setShouldPersist(false);
    options.getLogger().log(SentryLevel.WARNING, "Failed to write to a hang sample store", e);
  }

  private void cancelRecord() {
    openHangRecord = null;
    recordingSubscribers = Collections.emptyList();
  }

  private void endRecord(final long endTimeMs, final boolean endedAtMaxRecordingTime) {
    final @Nullable HangRecord.Builder builder = openHangRecord;
    if (builder == null) {
      return;
    }
    final @NotNull List<HangRecorderListener> listeners = new ArrayList<>();
    for (final @NotNull HangRecorderListener listener : recordingSubscribers) {
      if (subscriptions.contains(listener)) {
        listeners.add(listener);
      }
    }
    cancelRecord();
    if (listeners.isEmpty()) {
      return;
    }
    final @NotNull HangRecord record =
        builder
            .setDurationMs(endTimeMs - builder.getStartTimeMs())
            .setEndedAtMaxRecordingTime(endedAtMaxRecordingTime)
            .build();
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

  private long nowMs() {
    return TimeUnit.NANOSECONDS.toMillis(ticker.tickNanos());
  }
}
