package io.sentry.instrumentation.file;

import io.sentry.ISpan;
import java.io.File;
import java.io.FileInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FileInputStreamInitData {

  final @Nullable File file;
  final @Nullable ISpan span;
  final @NotNull FileInputStream delegate;

  public FileInputStreamInitData(
    final @Nullable File file,
    final @Nullable ISpan span,
    final @NotNull FileInputStream delegate
  ) {
    this.file = file;
    this.span = span;
    this.delegate = delegate;
  }
}
