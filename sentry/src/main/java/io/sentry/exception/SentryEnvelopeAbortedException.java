package io.sentry.exception;

import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when an envelope can't be serialized completely after part of it was written, for example
 * because a file payload changed or became unreadable. The partial output is invalid and must not
 * be sent or kept.
 */
@ApiStatus.Internal
public final class SentryEnvelopeAbortedException extends IOException {

  private static final long serialVersionUID = 4370390935163224155L;

  public SentryEnvelopeAbortedException(final @Nullable String message) {
    super(message);
  }

  public SentryEnvelopeAbortedException(
      final @Nullable String message, final @Nullable Throwable cause) {
    super(message, cause);
  }
}
