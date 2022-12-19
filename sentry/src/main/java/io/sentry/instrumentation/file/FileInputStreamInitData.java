package io.sentry.instrumentation.file;

import io.sentry.ISpan;
import io.sentry.SentryOptions;
import java.io.File;
import java.io.FileInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FileInputStreamInitData {

  final @Nullable File file;
  final @Nullable ISpan span;
  final @NotNull FileInputStream delegate;
  final @NotNull SentryOptions options;

  FileInputStreamInitData(
      final @Nullable File file,
      final @Nullable ISpan span,
      final @NotNull FileInputStream delegate,
      final @NotNull SentryOptions options) {
    this.file = file;
    this.span = span;
    this.delegate = delegate;
    this.options = options;
  }
}
