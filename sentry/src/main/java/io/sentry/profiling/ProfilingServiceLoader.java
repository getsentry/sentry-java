package io.sentry.profiling;

import io.sentry.IContinuousProfiler;
import io.sentry.ILogger;
import io.sentry.IProfileConverter;
import io.sentry.ISentryExecutorService;
import io.sentry.NoOpContinuousProfiler;
import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ProfilingServiceLoader {

  public static @NotNull IContinuousProfiler loadContinuousProfiler(
      ILogger logger,
      String profilingTracesDirPath,
      int profilingTracesHz,
      ISentryExecutorService executorService) {
    try {
      JavaContinuousProfilerProvider provider =
          loadSingleProvider(JavaContinuousProfilerProvider.class);

      if (provider != null) {
        logger.log(
            SentryLevel.DEBUG,
            "Loaded continuous profiler from provider: %s",
            provider.getClass().getName());
        return provider.getContinuousProfiler(
            logger, profilingTracesDirPath, profilingTracesHz, executorService);
      }

      logger.log(
          SentryLevel.DEBUG, "No continuous profiler provider found, using NoOpContinuousProfiler");
      return NoOpContinuousProfiler.getInstance();
    } catch (Throwable t) {
      logger.log(
          SentryLevel.ERROR,
          "Failed to load continuous profiler provider, using NoOpContinuousProfiler",
          t);
      return NoOpContinuousProfiler.getInstance();
    }
  }

  /**
   * Loads a profile converter using ServiceLoader discovery pattern.
   *
   * @return an IProfileConverter instance or null if no provider is found
   */
  public static @Nullable IProfileConverter loadProfileConverter() {
    ILogger logger = ScopesAdapter.getInstance().getGlobalScope().getOptions().getLogger();
    try {
      JavaProfileConverterProvider provider =
          loadSingleProvider(JavaProfileConverterProvider.class);
      if (provider != null) {
        logger.log(
            SentryLevel.DEBUG,
            "Loaded profile converter from provider: %s",
            provider.getClass().getName());
        return provider.getProfileConverter();
      } else {
        logger.log(SentryLevel.DEBUG, "No profile converter provider found, returning null");
        return null;
      }
    } catch (Throwable t) {
      logger.log(SentryLevel.ERROR, "Failed to load profile converter provider, returning null", t);
      return null;
    }
  }

  private static @Nullable <T> T loadSingleProvider(Class<T> clazz) {
    final ServiceLoader<T> serviceLoader = ServiceLoader.load(clazz);
    final Iterator<T> iterator = serviceLoader.iterator();

    if (iterator.hasNext()) {
      return iterator.next();
    } else {
      return null;
    }
  }
}

