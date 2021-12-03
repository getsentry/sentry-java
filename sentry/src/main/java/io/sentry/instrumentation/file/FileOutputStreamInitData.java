package io.sentry.instrumentation.file;

import io.sentry.ISpan;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FileOutputStreamInitData {

  @Nullable File file;
  @Nullable ISpan span;
  boolean append;
  @NotNull FileOutputStream delegate;

  public FileOutputStreamInitData(@Nullable File file, boolean append, @Nullable ISpan span,
    @NotNull FileOutputStream delegate) {
    this.file = file;
    this.append = append;
    this.span = span;
    this.delegate = delegate;
  }
}
