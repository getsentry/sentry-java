package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Reads one value from a data source. */
@ApiStatus.Internal
public interface HangDataCollector<T> {

  /** One value, or null for no value. Must be fast, no IO. */
  @Nullable
  T sample();
}
