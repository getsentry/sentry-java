package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * The runtime counters at one point in time. GC counters are cumulative for the process, times are
 * in milliseconds and memory is in bytes. A value the runtime does not provide is {@code null}.
 */
@ApiStatus.Internal
public final class RuntimeStatsSnapshot {

  public @Nullable Long gcCount;
  public @Nullable Double gcTimeMs;
  public @Nullable Long blockingGcCount;
  public @Nullable Double blockingGcTimeMs;
  public @Nullable Long preOomeGcCount;
  public @Nullable Double gcWaitingTimeMs;
  public @Nullable Long freeMemory;
  public @Nullable Long freeMemoryUntilOome;
  public @Nullable Long totalMemory;
  public @Nullable Long maxMemory;
}
