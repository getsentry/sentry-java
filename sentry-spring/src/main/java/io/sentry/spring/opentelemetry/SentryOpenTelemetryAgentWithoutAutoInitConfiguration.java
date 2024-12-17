package io.sentry.spring.opentelemetry;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Sentry;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryOpenTelemetryMode;
import io.sentry.SentryOptions;
import io.sentry.opentelemetry.OpenTelemetryUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Open
public class SentryOpenTelemetryAgentWithoutAutoInitConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "sentryOpenTelemetryOptionsConfiguration")
  public @NotNull Sentry.OptionsConfiguration<SentryOptions>
      sentryOpenTelemetryOptionsConfiguration() {
    return options -> {
      SentryIntegrationPackageStorage.getInstance()
          .addIntegration("SpringBootOpenTelemetryAgentWithoutAutoInit");
      OpenTelemetryUtil.applyOpenTelemetryOptions(options, SentryOpenTelemetryMode.AGENT);
    };
  }
}
