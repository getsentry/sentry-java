package io.sentry.android.core.hang;

import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads one value of a data source on each tick of a hang interval. Called only on the recorder
 * thread.
 */
@ApiStatus.Internal
public interface HangDataCollector<T> extends Closeable {

  /** One value for this tick, or null for no value. Must be fast, no IO. */
  @Nullable
  T sample(@NotNull HangInterval interval);

  /** One time, when the recorder closes. */
  @Override
  void close() throws IOException;
}
