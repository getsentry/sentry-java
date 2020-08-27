package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.EventProcessor;
import io.sentry.core.HubAdapter;
import io.sentry.core.IHub;
import io.sentry.core.Integration;
import io.sentry.core.Sentry;
import io.sentry.core.SentryOptions;
import io.sentry.core.protocol.SdkVersion;
import io.sentry.core.transport.ITransport;
import io.sentry.core.transport.ITransportGate;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@ConditionalOnProperty(name = "sentry.enabled", havingValue = "true", matchIfMissing = true)
@Open
public class SentryAutoConfiguration {

  /** Registers general purpose Sentry related beans. */
  @Configuration
  @ConditionalOnProperty("sentry.dsn")
  @EnableConfigurationProperties(SentryProperties.class)
  @Open
  static class HubConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public @NotNull Sentry.OptionsConfiguration<SentryOptions> optionsOptionsConfiguration(
        final @NotNull ObjectProvider<SentryOptions.BeforeSendCallback> beforeSendCallback,
        final @NotNull ObjectProvider<SentryOptions.BeforeBreadcrumbCallback>
                beforeBreadcrumbCallback,
        final @NotNull ObjectProvider<EventProcessor> eventProcessors,
        final @NotNull ObjectProvider<Integration> integrations,
        final @NotNull ObjectProvider<ITransportGate> transportGate,
        final @NotNull ObjectProvider<ITransport> transport) {
      return options -> {
        beforeSendCallback.ifAvailable(options::setBeforeSend);
        beforeBreadcrumbCallback.ifAvailable(options::setBeforeBreadcrumb);
        eventProcessors.stream().forEach(options::addEventProcessor);
        integrations.stream().forEach(options::addIntegration);
        transportGate.ifAvailable(options::setTransportGate);
        transport.ifAvailable(options::setTransport);
      };
    }

    @Bean
    public @NotNull SentryOptions sentryOptions(
        final @NotNull Sentry.OptionsConfiguration<SentryOptions> optionsConfiguration,
        final @NotNull SentryProperties properties,
        final @NotNull ObjectProvider<GitProperties> gitProperties) {
      final SentryOptions options = new SentryOptions();
      optionsConfiguration.configure(options);
      gitProperties.ifAvailable(
          git -> {
            if (properties.isUseGitCommitIdAsRelease()) {
              options.setRelease(git.getCommitId());
            }
          });
      properties.applyTo(options);
      options.setSentryClientName(BuildConfig.SENTRY_SPRING_BOOT_SDK_NAME);
      options.setSdkVersion(createSdkVersion(options));
      return options;
    }

    @Bean
    public @NotNull IHub sentryHub(final @NotNull SentryOptions sentryOptions) {
      Sentry.init(sentryOptions);
      return HubAdapter.getInstance();
    }

    /** Registers beans specific to Spring MVC. */
    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty("sentry.dsn")
    @Open
    static class SentryWebMvcConfiguration {

      @Bean
      public @NotNull FilterRegistrationBean<SentryRequestFilter> sentryRequestFilter(
          final @NotNull IHub sentryHub) {
        FilterRegistrationBean<SentryRequestFilter> filterRegistrationBean =
            new FilterRegistrationBean<>(new SentryRequestFilter(sentryHub));
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filterRegistrationBean;
      }
    }

    private static @NotNull SdkVersion createSdkVersion(
        final @NotNull SentryOptions sentryOptions) {
      SdkVersion sdkVersion = sentryOptions.getSdkVersion();

      if (sdkVersion == null) {
        sdkVersion = new SdkVersion();
      }

      sdkVersion.setName(BuildConfig.SENTRY_SPRING_BOOT_SDK_NAME);
      final String version = BuildConfig.VERSION_NAME;
      sdkVersion.setVersion(version);
      sdkVersion.addPackage("maven:sentry-spring-boot-starter", version);

      return sdkVersion;
    }
  }
}
