package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Reads one value of a data source on each sampling tick of a recorded hang. Called only on the
 * recorder thread.
 */
@ApiStatus.Internal
public interface HangDataCollector<T> {

  /** One value for this tick, or null for no value. Must be fast, no IO. */
  @Nullable
  T sample();
}
