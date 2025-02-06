package io.sentry.spring.jakarta.opentelemetry;

import com.jakewharton.nopen.annotation.Open;
import io.opentelemetry.api.OpenTelemetry;
import io.sentry.ISpanFactory;
import io.sentry.Sentry;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryOpenTelemetryMode;
import io.sentry.SentryOptions;
import io.sentry.opentelemetry.OtelSpanFactory;
import io.sentry.opentelemetry.SentryAutoConfigurationCustomizerProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Open
public class SentryOpenTelemetryNoAgentConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public static ISpanFactory openTelemetrySpanFactory(OpenTelemetry openTelemetry) {
    return new OtelSpanFactory(openTelemetry);
  }

  @Bean
  @ConditionalOnMissingBean(name = "sentryOpenTelemetryOptionsConfiguration")
  public @NotNull Sentry.OptionsConfiguration<SentryOptions>
      sentryOpenTelemetryOptionsConfiguration() {
    return options -> {
      SentryIntegrationPackageStorage.getInstance()
          .addIntegration("SpringBoot3OpenTelemetryNoAgent");
      SentryAutoConfigurationCustomizerProvider.skipInit = true;
      options.setOpenTelemetryMode(SentryOpenTelemetryMode.AGENTLESS_SPRING);
    };
  }
}
