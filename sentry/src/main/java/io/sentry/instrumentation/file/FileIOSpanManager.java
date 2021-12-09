package io.sentry.instrumentation.file;

import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import io.sentry.util.Pair;
import io.sentry.util.Platform;
import io.sentry.util.StringUtils;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FileIOSpanManager {

  private final @Nullable ISpan currentSpan;
  private final @Nullable File file;
  private final @NotNull IHub hub;

  private @Nullable Throwable throwable = null;
  private @NotNull SpanStatus spanStatus = SpanStatus.OK;
  private long byteCount;

  static @Nullable ISpan startSpan(final @NotNull IHub hub, final @NotNull String op) {
    final ISpan parent = hub.getSpan();
    return parent != null ? parent.startChild(op) : null;
  }

  FileIOSpanManager(
    final @Nullable ISpan currentSpan,
    final @Nullable File file,
    final @NotNull IHub hub
  ) {
    this.currentSpan = currentSpan;
    this.file = file;
    this.hub = hub;
  }

  /**
   * Performs file IO, counts the read/written bytes and handles exceptions in case of occurence
   *
   * @param operation An IO operation to execute (e.g. {@link FileInputStream#read()} or {@link FileOutputStream#write(int)}
   * The operation is of a type {@link Pair}, where the first element is the result of the IO operation,
   * and the second element is the number of bytes read/written/skipped/etc.
   */
  <T> T performIO(final @NotNull FileIOCallable<Pair<T, T>> operation)
    throws IOException {
    try {
      final Pair<T, T> result = operation.call();
      final T res = result.getFirst();
      if (res instanceof Integer) {
        final int resUnboxed = (int) result.getFirst();
        final int count = (int) result.getSecond();
        if (resUnboxed != -1) {
          byteCount += count;
        }
      } else if (res instanceof Long) {
        final long resUnboxed = (long) result.getFirst();
        final long count = (long) result.getSecond();
        if (resUnboxed != -1L) {
          byteCount += count;
        }
      }
      return Objects.requireNonNull(res, "Result of File IO is required");
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throwable = exception;
      throw exception;
    }
  }

  void finish(final @NotNull Closeable delegate) throws IOException {
    try {
      delegate.close();
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throwable = exception;
      throw exception;
    } finally {
      finishSpan();
    }
  }

  private void finishSpan() {
    if (currentSpan != null) {
      final String byteCountToString = StringUtils.byteCountToString(byteCount);
      if (file != null) {
        final String description = file.getName()
          + " "
          + "("
          + byteCountToString
          + ")";
        currentSpan.setDescription(description);
        if (Platform.isAndroid() || hub.getOptions().isSendDefaultPii()) {
          currentSpan.setData("file.path", file.getAbsolutePath());
        }
      } else {
        currentSpan.setDescription(byteCountToString);
      }
      currentSpan.setData("file.size", byteCount);
      currentSpan.setThrowable(throwable);
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
