package io.sentry.exception;

import org.jetbrains.annotations.Nullable;

public final class SentryEnvelopeException extends Exception {

  private static final long serialVersionUID = -8307801916948173232L;

  public SentryEnvelopeException(final @Nullable String message) {
    super(message);
  }
}
