package io.sentry.android.core.hang;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the runtime counters on each tick of a hang interval. The record gets the values of the
 * last tick and the change of the GC counters from the first to the last tick, see the {@code
 * KEY_*} constants.
 */
@ApiStatus.Internal
public final class RuntimeStatsCollector implements HangDataCollector<RuntimeStatsSnapshot> {

  public static final @NotNull String KEY_GC_COUNT = "gc_count";
  public static final @NotNull String KEY_GC_TIME_MS = "gc_time_ms";
  public static final @NotNull String KEY_BLOCKING_GC_COUNT = "blocking_gc_count";
  public static final @NotNull String KEY_BLOCKING_GC_TIME_MS = "blocking_gc_time_ms";
  public static final @NotNull String KEY_PRE_OOME_GC_COUNT = "pre_oome_gc_count";
  public static final @NotNull String KEY_GC_WAITING_TIME_MS = "gc_waiting_time_ms";
  public static final @NotNull String KEY_FREE_MEMORY = "free_memory";
  public static final @NotNull String KEY_FREE_MEMORY_UNTIL_OOME = "free_memory_until_oome";
  public static final @NotNull String KEY_TOTAL_MEMORY = "total_memory";
  public static final @NotNull String KEY_MAX_MEMORY = "max_memory";

  public static final @NotNull String KEY_GC_COUNT_DELTA = "gc_count_delta";
  public static final @NotNull String KEY_GC_TIME_MS_DELTA = "gc_time_ms_delta";
  public static final @NotNull String KEY_BLOCKING_GC_COUNT_DELTA = "blocking_gc_count_delta";
  public static final @NotNull String KEY_BLOCKING_GC_TIME_MS_DELTA = "blocking_gc_time_ms_delta";

  private final @NotNull RuntimeStatsSource source;

  public RuntimeStatsCollector(final @NotNull RuntimeStatsSource source) {
    this.source = source;
  }

  @Override
  public @NotNull RuntimeStatsSnapshot onSample(final @NotNull HangInterval interval) {
    return source.read();
  }

  @Override
  public void onRecordingStopped(
      final @NotNull List<RuntimeStatsSnapshot> samples, final @NotNull HangRecord.Builder out) {
    if (samples.isEmpty()) {
      return;
    }
    final @NotNull RuntimeStatsSnapshot start = samples.get(0);
    final @NotNull RuntimeStatsSnapshot end = samples.get(samples.size() - 1);

    final @NotNull Map<String, Object> stats = new HashMap<>();
    put(stats, KEY_GC_COUNT, end.gcCount);
    put(stats, KEY_GC_TIME_MS, end.gcTimeMs);
    put(stats, KEY_BLOCKING_GC_COUNT, end.blockingGcCount);
    put(stats, KEY_BLOCKING_GC_TIME_MS, end.blockingGcTimeMs);
    put(stats, KEY_PRE_OOME_GC_COUNT, end.preOomeGcCount);
    put(stats, KEY_GC_WAITING_TIME_MS, end.gcWaitingTimeMs);
    put(stats, KEY_FREE_MEMORY, end.freeMemory);
    put(stats, KEY_FREE_MEMORY_UNTIL_OOME, end.freeMemoryUntilOome);
    put(stats, KEY_TOTAL_MEMORY, end.totalMemory);
    put(stats, KEY_MAX_MEMORY, end.maxMemory);

    if (end.gcCount != null && start.gcCount != null) {
      stats.put(KEY_GC_COUNT_DELTA, end.gcCount - start.gcCount);
    }
    if (end.gcTimeMs != null && start.gcTimeMs != null) {
      stats.put(KEY_GC_TIME_MS_DELTA, end.gcTimeMs - start.gcTimeMs);
    }
    if (end.blockingGcCount != null && start.blockingGcCount != null) {
      stats.put(KEY_BLOCKING_GC_COUNT_DELTA, end.blockingGcCount - start.blockingGcCount);
    }
    if (end.blockingGcTimeMs != null && start.blockingGcTimeMs != null) {
      stats.put(KEY_BLOCKING_GC_TIME_MS_DELTA, end.blockingGcTimeMs - start.blockingGcTimeMs);
    }

    out.setRuntimeStats(stats);
  }

  @Override
  public void close() {}

  private static void put(
      final @NotNull Map<String, Object> map, final @NotNull String key, final @Nullable Object v) {
    if (v != null) {
      map.put(key, v);
    }
  }
}
