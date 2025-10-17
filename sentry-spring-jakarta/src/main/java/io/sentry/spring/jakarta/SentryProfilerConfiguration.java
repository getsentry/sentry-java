package io.sentry.spring.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IContinuousProfiler;
import io.sentry.IProfileConverter;
import io.sentry.NoOpContinuousProfiler;
import io.sentry.NoOpProfileConverter;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.profiling.ProfilingServiceLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Handles late initialization of the profiler if the application is run with the OTEL Agent in
 * auto-init mode. In that case the agent cannot initialize the profiler yet and falls back to No-Op
 * implementations. This Configuration sets the profiler and converter on the options if that was
 * the case.
 */
@Configuration(proxyBeanMethods = false)
@Open
public class SentryProfilerConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "sentryOpenTelemetryProfilerConfiguration")
  public IContinuousProfiler sentryOpenTelemetryProfilerConfiguration() {
    SentryOptions options = Sentry.getGlobalScope().getOptions();
    IContinuousProfiler profiler = NoOpContinuousProfiler.getInstance();

    if (Sentry.isEnabled()
        && options.isContinuousProfilingEnabled()
        && options.getContinuousProfiler() instanceof NoOpContinuousProfiler) {

      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Continuous profiler is NoOp, attempting to reload with Spring Boot classloader");

      String path = options.getProfilingTracesDirPath();

      profiler =
          ProfilingServiceLoader.loadContinuousProfiler(
              options.getLogger(),
              path != null ? path : "",
              options.getProfilingTracesHz(),
              options.getExecutorService());

      options.setContinuousProfiler(profiler);

      if (!(profiler instanceof NoOpContinuousProfiler)) {
        options
            .getLogger()
            .log(
                SentryLevel.INFO,
                "Successfully loaded continuous profiler via Spring Boot classloader");
      }
    }
    return profiler;
  }

  @Bean
  @ConditionalOnMissingBean(name = "sentryOpenTelemetryProfilerConverterConfiguration")
  public IProfileConverter sentryOpenTelemetryProfilerConverterConfiguration() {
    SentryOptions options = Sentry.getGlobalScope().getOptions();
    IProfileConverter converter = NoOpProfileConverter.getInstance();

    if (Sentry.isEnabled()
        && options.isContinuousProfilingEnabled()
        && options.getProfilerConverter() instanceof NoOpProfileConverter) {

      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Profile converter is NoOp, attempting to reload with Spring Boot classloader");

      converter = ProfilingServiceLoader.loadProfileConverter();

      options.setProfilerConverter(converter);

      if (!(converter instanceof NoOpProfileConverter)) {
        options
            .getLogger()
            .log(
                SentryLevel.INFO,
                "Successfully loaded profile converter via Spring Boot classloader");
      }
    }
    return converter;
  }
}
