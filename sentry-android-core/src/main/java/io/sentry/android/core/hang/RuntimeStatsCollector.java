package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Reads the runtime counters on each tick of a hang interval. */
@ApiStatus.Internal
public final class RuntimeStatsCollector implements HangDataCollector<RuntimeStatsSnapshot> {

  private final @NotNull RuntimeStatsSource source;

  public RuntimeStatsCollector(final @NotNull RuntimeStatsSource source) {
    this.source = source;
  }

  @Override
  public @NotNull RuntimeStatsSnapshot sample(final @NotNull HangInterval interval) {
    return source.read();
  }

  @Override
  public void close() {}
}
