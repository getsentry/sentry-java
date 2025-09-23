package io.sentry.asyncprofiler.provider;

import io.sentry.IContinuousProfiler;
import io.sentry.ILogger;
import io.sentry.ISentryExecutorService;
import io.sentry.NoOpContinuousProfiler;
import io.sentry.SentryLevel;
import io.sentry.asyncprofiler.profiling.JavaContinuousProfiler;
import io.sentry.profiling.JavaContinuousProfilerProvider;
import io.sentry.profiling.JavaProfileConverterProvider;
import org.jetbrains.annotations.NotNull;

/**
 * AsyncProfiler implementation of {@link JavaContinuousProfilerProvider} and {@link
 * JavaProfileConverterProvider}. This provider integrates AsyncProfiler with Sentry's continuous
 * profiling system and provides profile conversion functionality.
 */
public final class AsyncProfilerContinuousProfilerProvider
    implements JavaContinuousProfilerProvider {

  @Override
  public @NotNull IContinuousProfiler getContinuousProfiler(
      ILogger logger,
      String profilingTracesDirPath,
      int profilingTracesHz,
      ISentryExecutorService executorService) {
    try {
      return new JavaContinuousProfiler(
          logger, profilingTracesDirPath, profilingTracesHz, executorService);
    } catch (Exception e) {
      logger.log(
          SentryLevel.WARNING,
          "Failed to initialize AsyncProfiler. Profiling will be disabled.",
          e);
      return NoOpContinuousProfiler.getInstance();
    }
  }
}
