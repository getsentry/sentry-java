package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Reads the runtime counters on each sampling tick of a recorded hang. */
@ApiStatus.Internal
public final class RuntimeStatsCollector implements HangDataCollector<RuntimeStatsSnapshot> {

  private final @NotNull RuntimeStatsSource source;

  public RuntimeStatsCollector(final @NotNull RuntimeStatsSource source) {
    this.source = source;
  }

  @Override
  public @NotNull RuntimeStatsSnapshot sample() {
    return source.read();
  }
}
