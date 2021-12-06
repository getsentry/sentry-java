package io.sentry.instrumentation.file;

import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import io.sentry.util.StringUtils;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class FileIOSpanManager {

  private final @Nullable ISpan currentSpan;
  private final @Nullable File file;

  private @NotNull SpanStatus spanStatus = SpanStatus.OK;
  private long byteCount;

  static @Nullable ISpan startSpan(final @NotNull String op) {
    final ISpan parent = Sentry.getSpan();
    return parent != null ? parent.startChild(op) : null;
  }

  FileIOSpanManager(
    final @Nullable ISpan currentSpan,
    final @Nullable File file
  ) {
    this.currentSpan = currentSpan;
    this.file = file;
  }

  <T> T performIO(final @NotNull FileIOCallable<T> operation) throws IOException {
    try {
      final T result = operation.call();
      if (result instanceof Integer) {
        final int res = (int) result;
        if (res != -1) {
          byteCount += res;
        }
      } else if (result instanceof Long) {
        final long res = (long) result;
        if (res != -1) {
          byteCount += res;
        }
      }
      return result;
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throw exception;
    }
  }

  void finish(final @NotNull Closeable delegate) throws IOException {
    try {
      delegate.close();
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throw exception;
    }
    finishSpan();
  }

  private void finishSpan() {
    if (currentSpan != null) {
      String description;
      if (file != null) {
        description = file.getName()
          + " "
          + "("
          + StringUtils.byteCountToString(byteCount)
          + ")";
        currentSpan.setDescription(description);
        currentSpan.setData("file.path", file.getAbsolutePath());
      } else {
        description = StringUtils.byteCountToString(byteCount);
        currentSpan.setDescription(description);
      }
      currentSpan.setData("file.size", byteCount);
      currentSpan.finish(spanStatus);
    }
  }

  /**
   * A task that returns a result and may throw an IOException.
   * Implementors define a single method with no arguments called
   * {@code call}.
   *
   * Derived from {@link java.util.concurrent.Callable}
   */
  @FunctionalInterface
  interface FileIOCallable<V> {

    V call() throws IOException;
  }
}
