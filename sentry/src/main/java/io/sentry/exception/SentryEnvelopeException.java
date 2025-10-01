package io.sentry.exception;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when there was an issue reading/creating the envelope. Examples: Failed to read the file.
 * The file path does not exist. The file exceed the limit in size.
 */
@ApiStatus.Internal
public final class SentryEnvelopeException extends Exception {

  private static final long serialVersionUID = -8307801916948173232L;

  public SentryEnvelopeException(final @Nullable String message) {
    super(message);
  }

  public SentryEnvelopeException(final @Nullable String message, final @Nullable Throwable cause) {
    super(message, cause);
  }
}
