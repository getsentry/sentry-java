package io.sentry.android.core.hang;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Records one hang: asks the listeners what they need, samples the collectors into the record, and
 * writes the samples to the stores.
 */
final class HangRecorder {

  private final @NotNull List<HangCollectorSetup<?>> collectors;
  private final @NotNull List<HangRecordingListener> listeners;
  private final @NotNull ILogger logger;
  private final long startTimeMs;
  private final @NotNull HangRecord.Builder record;
  private boolean persist;
  private int samplingTicks;

  private HangRecorder(
      final @NotNull List<HangCollectorSetup<?>> collectors,
      final @NotNull List<HangRecordingListener> listeners,
      final @NotNull ILogger logger,
      final long startTimeMs,
      final long startTimestampMs,
      final boolean persist) {
    this.collectors = collectors;
    this.listeners = listeners;
    this.logger = logger;
    this.startTimeMs = startTimeMs;
    this.record = new HangRecord.Builder().setStartTimestampMs(startTimestampMs);
    this.persist = persist;
  }

  /**
   * Asks each listener what it needs from this hang, and clears the stores if one asks to persist.
   *
   * @param startTimeMs the start of the hang, as a monotonic time in milliseconds
   * @param startTimestampMs the start of the hang, as Unix time in milliseconds
   * @return null if no listener asks for a record
   */
  static @Nullable HangRecorder start(
      final @NotNull Collection<HangRecordingListener> subscriptions,
      final @NotNull List<HangCollectorSetup<?>> collectors,
      final long startTimeMs,
      final long startTimestampMs,
      final @NotNull ILogger logger) {
    boolean persist = false;
    final @NotNull List<HangRecordingListener> listeners = new ArrayList<>();
    for (final @NotNull HangRecordingListener listener : subscriptions) {
      final @Nullable HangRecordingRequest request = listener.onHangStarted();
      if (request != null && request.shouldRecord()) {
        listeners.add(listener);
        if (request.shouldPersist()) {
          persist = true;
        }
      }
    }
    if (listeners.isEmpty()) {
      return null;
    }
    final @NotNull HangRecorder recorder =
        new HangRecorder(collectors, listeners, logger, startTimeMs, startTimestampMs, persist);
    if (persist) {
      recorder.clearStores();
    }
    return recorder;
  }

  /** The start of the hang, as a monotonic time in milliseconds. */
  long getStartTimeMs() {
    return startTimeMs;
  }

  /**
   * Samples each collector one time, at most {@code maxSamples} times for this hang. A store error
   * stops the persistence of this hang.
   */
  void sample(final int maxSamples) {
    // Stop sampling at the cap instead of rotating out samples: the early ones show how the hang
    // began.
    if (samplingTicks >= maxSamples) {
      return;
    }
    samplingTicks++;
    for (final @NotNull HangCollectorSetup<?> setup : collectors) {
      try {
        sample(setup, record, persist);
      } catch (IOException e) {
        stopPersisting(e);
      }
    }
  }

  /** The listeners that asked for the record of this hang. */
  @NotNull
  List<HangRecordingListener> getListeners() {
    return listeners;
  }

  /** Builds the record of this hang. */
  @NotNull
  HangRecord end(final long endTimeMs, final boolean endedAtMaxRecordingTime) {
    return record
        .setDurationMs(endTimeMs - startTimeMs)
        .setEndedAtMaxRecordingTime(endedAtMaxRecordingTime)
        .build();
  }

  private void clearStores() {
    for (final @NotNull HangCollectorSetup<?> setup : collectors) {
      final @Nullable HangSampleStore<?> store = setup.store;
      if (store == null) {
        continue;
      }
      try {
        store.clear();
      } catch (IOException e) {
        stopPersisting(e);
        return;
      }
    }
  }

  private static <T> void sample(
      final @NotNull HangCollectorSetup<T> setup,
      final @NotNull HangRecord.Builder record,
      final boolean persist)
      throws IOException {
    final @Nullable T sample = setup.collector.sample();
    if (sample == null) {
      return;
    }
    setup.writer.write(sample, record);
    if (persist && setup.store != null) {
      setup.store.add(sample);
    }
  }

  private void stopPersisting(final @NotNull IOException e) {
    persist = false;
    logger.log(SentryLevel.WARNING, "Failed to write to a hang sample store", e);
  }
}
