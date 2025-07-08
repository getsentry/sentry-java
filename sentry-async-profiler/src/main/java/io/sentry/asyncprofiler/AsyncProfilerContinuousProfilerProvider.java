package io.sentry.asyncprofiler;

import io.sentry.IContinuousProfiler;
import io.sentry.ILogger;
import io.sentry.ISentryExecutorService;
import io.sentry.profiling.JavaContinuousProfilerProvider;
import io.sentry.profiling.JavaProfileConverterProvider;
import io.sentry.protocol.profiling.JavaContinuousProfiler;
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
    return new JavaContinuousProfiler(
        logger,
        profilingTracesDirPath,
        10, // default profilingTracesHz
        executorService);
  }
}
