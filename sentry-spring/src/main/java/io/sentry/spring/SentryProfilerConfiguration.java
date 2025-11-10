package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IContinuousProfiler;
import io.sentry.IProfileConverter;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.util.InitUtil;
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

    if (Sentry.isEnabled()) {
      return InitUtil.initializeProfiler(options);
    } else {
      return options.getContinuousProfiler();
    }
  }

  @Bean
  @ConditionalOnMissingBean(name = "sentryOpenTelemetryProfilerConverterConfiguration")
  public IProfileConverter sentryOpenTelemetryProfilerConverterConfiguration() {
    SentryOptions options = Sentry.getGlobalScope().getOptions();

    if (Sentry.isEnabled()) {
      return InitUtil.initializeProfileConverter(options);
    } else {
      return options.getProfilerConverter();
    }
  }
}
