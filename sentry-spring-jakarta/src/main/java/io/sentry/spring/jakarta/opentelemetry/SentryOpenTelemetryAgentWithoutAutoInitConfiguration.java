package io.sentry.spring.jakarta.opentelemetry;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.ISpanFactory;
import io.sentry.Sentry;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryOptions;
import io.sentry.opentelemetry.OpenTelemetryUtil;
import io.sentry.opentelemetry.OtelSpanFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Open
public class SentryOpenTelemetryAgentWithoutAutoInitConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public static ISpanFactory openTelemetrySpanFactory() {
    return new OtelSpanFactory();
  }

  @Bean
  @ConditionalOnMissingBean(name = "sentryOpenTelemetryOptionsConfiguration")
  public @NotNull Sentry.OptionsConfiguration<SentryOptions>
      sentryOpenTelemetryOptionsConfiguration() {
    return options -> {
      SentryIntegrationPackageStorage.getInstance()
          .addIntegration("SpringBoot3OpenTelemetryAgentWithoutAutoInit");
      OpenTelemetryUtil.applyOpenTelemetryOptions(options);
    };
  }
}
