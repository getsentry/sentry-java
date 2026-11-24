package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Reads the runtime counters of the process. */
@ApiStatus.Internal
public interface RuntimeStatsSource {

  /** Must be cheap. */
  @NotNull
  RuntimeStatsSnapshot read();
}
