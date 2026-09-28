package io.sentry.exception;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** A user callback failure propagated by strict callback mode and excluded from event capture. */
@ApiStatus.Internal
public final class SentryCallbackException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SentryCallbackException(final @NotNull Throwable cause) {
    super("Sentry user callback failed in strict callback mode", cause);
  }
}
