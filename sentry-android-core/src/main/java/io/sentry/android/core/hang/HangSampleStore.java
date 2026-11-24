package io.sentry.android.core.hang;

import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Stores values on disk. */
@ApiStatus.Internal
public interface HangSampleStore<T> extends Closeable {

  /** Removes all stored values. */
  void clear() throws IOException;

  void add(@NotNull T sample) throws IOException;

  @Override
  void close() throws IOException;
}
