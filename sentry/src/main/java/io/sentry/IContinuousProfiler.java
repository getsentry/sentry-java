package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Used for performing operations when a transaction is started or ended. */
@ApiStatus.Internal
public interface IContinuousProfiler {
  boolean isRunning();

  void start(final @NotNull TracesSampler tracesSampler);

  void stop();

  /** Cancel the profiler and stops it. Used on SDK close. */
  void close();

  void reevaluateSampling();

  @NotNull
  SentryId getProfilerId();
}
