package io.sentry.android.core;

import android.os.Build;
import android.os.Debug;
import androidx.annotation.RequiresApi;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.android.core.hang.RuntimeStatsSnapshot;
import io.sentry.android.core.hang.RuntimeStatsSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the ART counters with {@link Debug#getRuntimeStat(String)} per key, which costs about 0.4
 * us. {@link Debug#getRuntimeStats()} is not used because its map has no value for some keys, and
 * {@link Debug#getMemoryInfo(Debug.MemoryInfo)} takes milliseconds.
 */
@ApiStatus.Internal
@RequiresApi(api = Build.VERSION_CODES.M)
public final class AndroidRuntimeStatsSource implements RuntimeStatsSource {

  static final @NotNull String GC_COUNT = "art.gc.gc-count";
  static final @NotNull String GC_TIME = "art.gc.gc-time";
  static final @NotNull String BLOCKING_GC_COUNT = "art.gc.blocking-gc-count";
  static final @NotNull String BLOCKING_GC_TIME = "art.gc.blocking-gc-time";
  static final @NotNull String PRE_OOME_GC_COUNT = "art.gc.pre-oome-gc-count";
  static final @NotNull String GC_WAITING_TIME = "art.gc.total-time-waiting-for-gc";

  private static final double NANOS_PER_MILLI = 1_000_000.0;

  interface StatReader {
    @Nullable
    String read(@NotNull String key);
  }

  private final @NotNull ILogger logger;
  private final @NotNull StatReader statReader;

  public AndroidRuntimeStatsSource(final @NotNull ILogger logger) {
    this(logger, Debug::getRuntimeStat);
  }

  AndroidRuntimeStatsSource(final @NotNull ILogger logger, final @NotNull StatReader statReader) {
    this.logger = logger;
    this.statReader = statReader;
  }

  @Override
  public @NotNull RuntimeStatsSnapshot read() {
    final @NotNull RuntimeStatsSnapshot snapshot = new RuntimeStatsSnapshot();
    snapshot.gcCount = readLong(GC_COUNT);
    snapshot.gcTimeMs = readDouble(GC_TIME);
    snapshot.blockingGcCount = readLong(BLOCKING_GC_COUNT);
    snapshot.blockingGcTimeMs = readDouble(BLOCKING_GC_TIME);
    snapshot.preOomeGcCount = readLong(PRE_OOME_GC_COUNT);

    // Undocumented unit; the values match nanoseconds.
    final @Nullable Double waitingNanos = readDouble(GC_WAITING_TIME);
    snapshot.gcWaitingTimeMs = waitingNanos == null ? null : waitingNanos / NANOS_PER_MILLI;

    final @NotNull Runtime runtime = Runtime.getRuntime();
    final long total = runtime.totalMemory();
    final long free = runtime.freeMemory();
    final long max = runtime.maxMemory();
    snapshot.freeMemory = free;
    snapshot.totalMemory = total;
    snapshot.maxMemory = max;
    // ART has no counter for this, the heap headroom is the closest value.
    snapshot.freeMemoryUntilOome = max - (total - free);
    return snapshot;
  }

  private @Nullable Long readLong(final @NotNull String key) {
    final @Nullable String value = statReader.read(key);
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      logUnparsable(key, value, e);
      return null;
    }
  }

  private @Nullable Double readDouble(final @NotNull String key) {
    final @Nullable String value = statReader.read(key);
    if (value == null) {
      return null;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      logUnparsable(key, value, e);
      return null;
    }
  }

  private void logUnparsable(
      final @NotNull String key, final @NotNull String value, final @NotNull Throwable e) {
    logger.log(SentryLevel.WARNING, e, "Could not parse the runtime stat %s: %s", key, value);
  }
}
