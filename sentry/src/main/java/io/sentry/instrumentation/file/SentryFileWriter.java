package io.sentry.instrumentation.file;

import io.sentry.IHub;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import org.jetbrains.annotations.NotNull;

public final class SentryFileWriter extends OutputStreamWriter {
  public SentryFileWriter(final @NotNull String fileName) throws FileNotFoundException {
    super(new SentryFileOutputStream(fileName));
  }

  public SentryFileWriter(final @NotNull String fileName, final boolean append)
      throws FileNotFoundException {
    super(new SentryFileOutputStream(fileName, append));
  }

  public SentryFileWriter(final @NotNull File file) throws FileNotFoundException {
    super(new SentryFileOutputStream(file));
  }

  public SentryFileWriter(final @NotNull File file, final boolean append)
      throws FileNotFoundException {
    super(new SentryFileOutputStream(file, append));
  }

  public SentryFileWriter(final @NotNull FileDescriptor fd) {
    super(new SentryFileOutputStream(fd));
  }

  SentryFileWriter(final @NotNull File file, final boolean append, final @NotNull IHub hub)
      throws FileNotFoundException {
    super(new SentryFileOutputStream(file, append, hub));
  }
}
