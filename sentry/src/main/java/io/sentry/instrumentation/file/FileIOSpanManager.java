package io.sentry.instrumentation.file;

import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryOptions;
import io.sentry.SentryStackTraceFactory;
import io.sentry.SpanDataConvention;
import io.sentry.SpanStatus;
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
  private final @NotNull SentryOptions options;

  private @NotNull SpanStatus spanStatus = SpanStatus.OK;
  private long byteCount;

  private final @NotNull SentryStackTraceFactory stackTraceFactory;

  static @Nullable ISpan startSpan(final @NotNull IHub hub, final @NotNull String op) {
    final ISpan parent = hub.getSpan();
    return parent != null ? parent.startChild(op) : null;
  }

  FileIOSpanManager(
      final @Nullable ISpan currentSpan,
      final @Nullable File file,
      final @NotNull SentryOptions options) {
    this.currentSpan = currentSpan;
    this.file = file;
    this.options = options;
    this.stackTraceFactory = new SentryStackTraceFactory(options);
    SentryIntegrationPackageStorage.getInstance().addIntegration("FileIO");
  }

  /**
   * Performs file IO, counts the read/written bytes and handles exceptions in case of occurence
   *
   * @param operation An IO operation to execute (e.g. {@link FileInputStream#read()} or {@link
   *     FileOutputStream#write(int)} The operation is of a type {@link Integer} or {@link Long},
   *     where the output is the result of the IO operation (byte count read/written)
   */
  <T> T performIO(final @NotNull FileIOCallable<T> operation) throws IOException {
    try {
      final T result = operation.call();
      if (result instanceof Integer) {
        final int resUnboxed = (int) result;
        if (resUnboxed != -1) {
          byteCount += resUnboxed;
        }
      } else if (result instanceof Long) {
        final long resUnboxed = (long) result;
        if (resUnboxed != -1L) {
          byteCount += resUnboxed;
        }
      }
      return result;
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      if (currentSpan != null) {
        currentSpan.setThrowable(exception);
      }
      throw exception;
    }
  }

  void finish(final @NotNull Closeable delegate) throws IOException {
    try {
      delegate.close();
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      if (currentSpan != null) {
        currentSpan.setThrowable(exception);
      }
      throw exception;
    } finally {
      finishSpan();
    }
  }

  private void finishSpan() {
    if (currentSpan != null) {
      final String byteCountToString = StringUtils.byteCountToString(byteCount);
      if (file != null) {
        final String description = file.getName() + " " + "(" + byteCountToString + ")";
        currentSpan.setDescription(description);
        if (Platform.isAndroid() || options.isSendDefaultPii()) {
          currentSpan.setData("file.path", file.getAbsolutePath());
        }
      } else {
        currentSpan.setDescription(byteCountToString);
      }
      currentSpan.setData("file.size", byteCount);
      final boolean isMainThread = options.getMainThreadChecker().isMainThread();
      currentSpan.setData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY, isMainThread);
      if (isMainThread) {
        currentSpan.setData(
            SpanDataConvention.CALL_STACK_KEY, stackTraceFactory.getInAppCallStack());
      }
      currentSpan.finish(spanStatus);
    }
  }

  /**
   * A task that returns a result and may throw an IOException. Implementors define a single method
   * with no arguments called {@code call}.
   *
   * <p>Derived from {@link java.util.concurrent.Callable}
   */
  @FunctionalInterface
  interface FileIOCallable<T> {

    T call() throws IOException;
  }
}
