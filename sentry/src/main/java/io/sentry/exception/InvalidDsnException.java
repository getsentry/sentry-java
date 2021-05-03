package io.sentry.exception;

import org.jetbrains.annotations.Nullable;

public final class InvalidDsnException extends RuntimeException {
  private static final long serialVersionUID = 412945154259913013L;
  private final @Nullable String dsn;

  public InvalidDsnException(final @Nullable String dsn) {
    this.dsn = dsn;
  }

  public InvalidDsnException(final @Nullable String dsn, final @Nullable String message) {
    super(message);
    this.dsn = dsn;
  }

  public InvalidDsnException(
      final @Nullable String dsn, final @Nullable String message, final @Nullable Throwable cause) {
    super(message, cause);
    this.dsn = dsn;
  }

  public InvalidDsnException(final @Nullable String dsn, final @Nullable Throwable cause) {
    super(cause);
    this.dsn = dsn;
  }

  public @Nullable String getDsn() {
    return dsn;
  }
}
