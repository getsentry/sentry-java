package io.sentry.profiling;

import io.sentry.IContinuousProfiler;
import io.sentry.ILogger;
import io.sentry.ISentryExecutorService;
import org.jetbrains.annotations.NotNull;

/**
 * Service provider interface for creating continuous profilers.
 *
 * <p>This interface allows for pluggable continuous profiler implementations that can be discovered
 * at runtime using the ServiceLoader mechanism.
 */
public interface JavaContinuousProfilerProvider {

  /**
   * Creates and returns a continuous profiler instance.
   *
   * @return a continuous profiler instance, or null if the provider cannot create one
   */
  @NotNull
  IContinuousProfiler getContinuousProfiler(
      ILogger logger,
      String profilingTracesDirPath,
      int profilingTracesHz,
      ISentryExecutorService executorService);
}
