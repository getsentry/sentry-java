package io.sentry.exception;

public final class SentryEnvelopeException extends Exception {

  private static final long serialVersionUID = -8307801916948173232L;

  public SentryEnvelopeException(String message) {
    super(message);
  }
}
