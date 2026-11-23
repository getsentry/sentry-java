package io.sentry.android.core.hang;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
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
  T onSample(@NotNull HangInterval interval);

  /**
   * The interval ended with a record.
   *
   * @param samples all non-null values of this interval, in order, at most the recorder's {@code
   *     maxSamples}
   */
  void onRecordingStopped(@NotNull List<T> samples, @NotNull HangRecord.Builder out);

  /** One time, when the recorder closes. */
  @Override
  void close() throws IOException;
}
