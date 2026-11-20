package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Reads the runtime counters of the process. */
@ApiStatus.Internal
public interface RuntimeStatsSource {

  /** Must be cheap, callers may read it on a time-critical thread. */
  @NotNull
  RuntimeStatsSnapshot read();
}
