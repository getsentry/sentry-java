package io.sentry.exception;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An error from a user callback propagated by strict callback mode without converting it to an
 * exception.
 */
@ApiStatus.Internal
public final class SentryCallbackError extends Error {
  private static final long serialVersionUID = 1L;

  public SentryCallbackError(final @NotNull Error cause) {
    super("Sentry user callback failed in strict callback mode", cause);
  }
}
