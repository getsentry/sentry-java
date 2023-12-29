package io.sentry.exception;

import io.sentry.SentryTraceHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when {@link SentryTraceHeader} fails to create because of the invalid value of the
 * "sentry-trace" header field.
 */
public final class InvalidSentryTraceHeaderException extends Exception {
  private static final long serialVersionUID = -8353316997083420940L;
  private final @NotNull String sentryTraceHeader;

  public InvalidSentryTraceHeaderException(final @NotNull String sentryTraceHeader) {
    this(sentryTraceHeader, null);
  }

  public InvalidSentryTraceHeaderException(
      final @NotNull String sentryTraceHeader, final @Nullable Throwable cause) {
    super("sentry-trace header does not conform to expected format: " + sentryTraceHeader, cause);
    this.sentryTraceHeader = sentryTraceHeader;
  }

  public @NotNull String getSentryTraceHeader() {
    return sentryTraceHeader;
  }
}
