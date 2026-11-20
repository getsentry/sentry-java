package io.sentry.android.core.hang;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.anr.AnrProfileManager;
import io.sentry.android.core.anr.AnrProfileRotationHelper;
import io.sentry.android.core.anr.AnrStackTrace;
import io.sentry.time.EpochClock;
import io.sentry.time.MonotonicTicker;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Samples the stack of a thread on each tick of a hang interval. The samples stay in memory until
 * the interval is {@link #DISK_THRESHOLD_MS} old, then they are written to the ANR profile file, so
 * the fatal ANR path can still read them if the process is killed.
 *
 * <p>Not thread-safe, {@link HangRecorder} calls it only on its recorder thread.
 */
@ApiStatus.Internal
public final class ThreadSampleCollector implements HangDataCollector {

  static final long DISK_THRESHOLD_MS = 700;

  private final @NotNull SentryOptions options;
  private final @NotNull EpochClock epochClock;
  private final @NotNull MonotonicTicker ticker;
  private final @NotNull Thread thread;
  private final @NotNull List<AnrStackTrace> samples = new ArrayList<>();
  private @Nullable AnrProfileManager profileManager;
  private boolean onDisk;

  public ThreadSampleCollector(
      final @NotNull SentryOptions options,
      final @NotNull EpochClock epochClock,
      final @NotNull MonotonicTicker ticker,
      final @NotNull Thread thread) {
    this.options = options;
    this.epochClock = epochClock;
    this.ticker = ticker;
    this.thread = thread;
  }

  @Override
  public void onRecordingStarted(final @NotNull HangInterval interval) {
    reset();
    if (profileManager == null) {
      final @Nullable String cacheDirPath = options.getCacheDirPath();
      if (cacheDirPath != null) {
        final @NotNull File file =
            AnrProfileRotationHelper.getFileForRecording(new File(cacheDirPath));
        profileManager = new AnrProfileManager(options, file);
      }
    }
  }

  @Override
  public void onSample(final @NotNull HangInterval interval) {
    if (samples.size() >= AnrProfileManager.MAX_NUM_STACKTRACES) {
      return;
    }

    final long epochMs = NANOSECONDS.toMillis(epochClock.now().epochNanos());
    final @NotNull AnrStackTrace sample =
        new AnrStackTrace(epochMs, thread.getStackTrace(), thread.getState());
    samples.add(sample);

    final long nowMs = NANOSECONDS.toMillis(ticker.tickNanos());
    // Most hangs end before this, so they cause no disk IO.
    if (nowMs - interval.getStartTimeMs() < DISK_THRESHOLD_MS) {
      return;
    }

    final @Nullable AnrProfileManager manager = profileManager;
    if (manager == null) {
      return;
    }

    try {
      if (!onDisk) {
        onDisk = true;
        startDiskRecording(manager);
      } else {
        manager.add(sample);
      }
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.WARNING, "Failed to write the thread samples", e);
    }
  }

  private void startDiskRecording(final @NotNull AnrProfileManager manager) throws IOException {
    manager.clear();
    for (final @NotNull AnrStackTrace buffered : samples) {
      manager.add(buffered);
    }
  }

  @Override
  public void onRecordingStopped(
      final @NotNull HangInterval interval, final @NotNull HangRecord.Builder out) {
    out.setSamples(new ArrayList<>(samples));
  }

  /** Discards the samples in memory only, the fatal path keeps the file. */
  @Override
  public void onRecordingCanceled() {
    reset();
  }

  @Override
  public void close() throws IOException {
    reset();
    final @Nullable AnrProfileManager manager = profileManager;
    profileManager = null;
    if (manager != null) {
      manager.close();
    }
  }

  private void reset() {
    samples.clear();
    onDisk = false;
  }
}
