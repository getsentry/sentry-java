package io.sentry.instrumentation.file;

import io.sentry.IHub;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import org.jetbrains.annotations.NotNull;

public final class SentryFileReader extends InputStreamReader {
  public SentryFileReader(final @NotNull String fileName) throws FileNotFoundException {
    super(new SentryFileInputStream(fileName));
  }

  public SentryFileReader(final @NotNull File file) throws FileNotFoundException {
    super(new SentryFileInputStream(file));
  }

  public SentryFileReader(final @NotNull FileDescriptor fd) {
    super(new SentryFileInputStream(fd));
  }

  SentryFileReader(final @NotNull File file, final @NotNull IHub hub) throws FileNotFoundException {
    super(new SentryFileInputStream(file, hub));
  }
}
