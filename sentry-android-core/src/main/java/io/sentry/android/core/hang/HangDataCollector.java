package io.sentry.android.core.hang;

import io.sentry.time.Timestamp;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Reads one value from a data source. */
@ApiStatus.Internal
public interface HangDataCollector<T> {

  /**
   * One value, or null for no value. Must be fast, no IO.
   *
   * @param timestamp the time of this sample, the same for all collectors of one tick
   */
  @Nullable
  T sample(@NotNull Timestamp timestamp);
}
