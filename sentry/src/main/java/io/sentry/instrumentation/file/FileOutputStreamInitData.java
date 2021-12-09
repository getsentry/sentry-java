package io.sentry.instrumentation.file;

import io.sentry.IHub;
import io.sentry.ISpan;
import java.io.File;
import java.io.FileOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FileOutputStreamInitData {

  final @Nullable File file;
  final @Nullable ISpan span;
  final boolean append;
  final @NotNull FileOutputStream delegate;
  final @NotNull IHub hub;

  public FileOutputStreamInitData(
    final @Nullable File file,
    final boolean append,
    final @Nullable ISpan span,
    final @NotNull FileOutputStream delegate,
    @NotNull IHub hub) {
    this.file = file;
    this.append = append;
    this.span = span;
    this.delegate = delegate;
    this.hub = hub;
  }
}
