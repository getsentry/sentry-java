package io.sentry.android.core.hang;

import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the values of one collector on disk, so a process kill does not lose them. Called only on
 * the recorder thread.
 */
@ApiStatus.Internal
public interface HangSampleStore<T> extends Closeable {

  /** Called one time for each persisted interval, before its first {@link #add(Object)}. */
  void clear() throws IOException;

  void add(@NotNull T sample) throws IOException;

  @Override
  void close() throws IOException;
}
