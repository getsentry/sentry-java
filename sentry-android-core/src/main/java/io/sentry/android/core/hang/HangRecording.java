package io.sentry.android.core.hang;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The recording of one hang: the listeners that asked for a record, the samples, and the stores.
 */
final class HangRecording {

  private final @NotNull List<HangRecorder.CollectorSetup<?>> collectors;
  private final @NotNull List<HangRecorderListener> listeners;
  private final @NotNull ILogger logger;
  private final long startTimeMs;
  private final @NotNull HangRecord.Builder record;
  private boolean persist;
  private int samplingTicks;

  private HangRecording(
      final @NotNull List<HangRecorder.CollectorSetup<?>> collectors,
      final @NotNull List<HangRecorderListener> listeners,
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
  static @Nullable HangRecording start(
      final @NotNull Collection<HangRecorderListener> subscriptions,
      final @NotNull List<HangRecorder.CollectorSetup<?>> collectors,
      final long startTimeMs,
      final long startTimestampMs,
      final @NotNull ILogger logger) {
    boolean persist = false;
    final @NotNull List<HangRecorderListener> listeners = new ArrayList<>();
    for (final @NotNull HangRecorderListener listener : subscriptions) {
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
    final @NotNull HangRecording recording =
        new HangRecording(collectors, listeners, logger, startTimeMs, startTimestampMs, persist);
    if (persist) {
      recording.clearStores();
    }
    return recording;
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
    for (final @NotNull HangRecorder.CollectorSetup<?> setup : collectors) {
      try {
        setup.sample(record, persist);
      } catch (IOException e) {
        stopPersisting(e);
      }
    }
  }

  /** The listeners that asked for the record of this hang and are in {@code subscriptions}. */
  @NotNull
  List<HangRecorderListener> recipients(final @NotNull Set<HangRecorderListener> subscriptions) {
    final @NotNull List<HangRecorderListener> recipients = new ArrayList<>();
    for (final @NotNull HangRecorderListener listener : listeners) {
      if (subscriptions.contains(listener)) {
        recipients.add(listener);
      }
    }
    return recipients;
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
    for (final @NotNull HangRecorder.CollectorSetup<?> setup : collectors) {
      try {
        setup.clearStore();
      } catch (IOException e) {
        stopPersisting(e);
        return;
      }
    }
  }

  private void stopPersisting(final @NotNull IOException e) {
    persist = false;
    logger.log(SentryLevel.WARNING, "Failed to write to a hang sample store", e);
  }
}
