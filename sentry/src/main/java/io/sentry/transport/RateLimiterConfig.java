package io.sentry.transport;

import io.sentry.ILogger;
import io.sentry.ISentryExecutorService;
import io.sentry.clientreport.IClientReportRecorder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The configuration {@link RateLimiter} reads. Declared next to its consumer rather than alongside
 * the implementation, so that the collaborators a rate limiter actually touches are three lines to
 * read instead of three hundred, and a test can supply them without building a {@link
 * io.sentry.SentryOptions}.
 *
 * <p>Implementations are expected to delegate to live configuration rather than snapshot it, so
 * that a logger or executor replaced after {@code Sentry.init} is still picked up.
 */
@ApiStatus.Internal
public interface RateLimiterConfig {

  @NotNull
  ILogger getLogger();

  @NotNull
  IClientReportRecorder getClientReportRecorder();

  @NotNull
  ISentryExecutorService getTimerExecutorService();
}
