package io.sentry.asyncprofiler.provider;

import io.sentry.IProfileConverter;
import io.sentry.asyncprofiler.convert.JfrAsyncProfilerToSentryProfileConverter;
import io.sentry.profiling.JavaProfileConverterProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * AsyncProfiler implementation of {@link JavaProfileConverterProvider}. This provider integrates
 * AsyncProfiler's JFR converter with Sentry's profiling system.
 */
@ApiStatus.Internal
public final class AsyncProfilerProfileConverterProvider implements JavaProfileConverterProvider {

  @Override
  public @NotNull IProfileConverter getProfileConverter() {
    return new AsyncProfilerProfileConverter();
  }

  /**
   * Internal implementation of IProfileConverter that delegates to
   * JfrAsyncProfilerToSentryProfileConverter.
   */
  public static final class AsyncProfilerProfileConverter implements IProfileConverter {

    @Override
    public @NotNull io.sentry.protocol.profiling.SentryProfile convertFromFile(
        @NotNull String jfrFilePath) throws java.io.IOException {
      return JfrAsyncProfilerToSentryProfileConverter.convertFromFileStatic(jfrFilePath);
    }
  }
}
