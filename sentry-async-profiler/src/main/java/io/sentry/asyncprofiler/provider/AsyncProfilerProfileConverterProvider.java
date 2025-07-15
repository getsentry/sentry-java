package io.sentry.asyncprofiler.provider;

import io.sentry.IProfileConverter;
import io.sentry.asyncprofiler.convert.JfrAsyncProfilerToSentryProfileConverter;
import io.sentry.profiling.JavaProfileConverterProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * AsyncProfiler implementation of {@link JavaProfileConverterProvider}. This provider integrates
 * AsyncProfiler's JFR converter with Sentry's profiling system.
 */
public final class AsyncProfilerProfileConverterProvider implements JavaProfileConverterProvider {

  @Override
  public @Nullable IProfileConverter getProfileConverter() {
    return new AsyncProfilerProfileConverter();
  }

  /**
   * Internal implementation of IProfileConverter that delegates to
   * JfrAsyncProfilerToSentryProfileConverter.
   */
  private static final class AsyncProfilerProfileConverter implements IProfileConverter {

    @Override
    public @NotNull io.sentry.protocol.profiling.SentryProfile convertFromFile(
        @NotNull java.nio.file.Path jfrFilePath) throws java.io.IOException {
      return JfrAsyncProfilerToSentryProfileConverter.convertFromFileStatic(jfrFilePath);
    }
  }
}
