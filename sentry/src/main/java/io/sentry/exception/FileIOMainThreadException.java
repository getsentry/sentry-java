package io.sentry.exception;

import org.jetbrains.annotations.Nullable;

public final class FileIOMainThreadException  extends Exception {
  private static final long serialVersionUID = 1L;

  public FileIOMainThreadException(final @Nullable String message) {
    super(message);
  }
}
