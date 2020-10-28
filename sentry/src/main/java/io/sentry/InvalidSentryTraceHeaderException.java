package io.sentry;

import org.jetbrains.annotations.NotNull;

public final class InvalidSentryTraceHeaderException extends Exception {
  private static final long serialVersionUID = 1L;
  private final String sentryTraceHeader;

  public InvalidSentryTraceHeaderException(final @NotNull String sentryTraceHeader) {
    super("sentry-trace header does not conform to expected format: " + sentryTraceHeader);
    this.sentryTraceHeader = sentryTraceHeader;
  }

  public String getSentryTraceHeader() {
    return sentryTraceHeader;
  }
}
