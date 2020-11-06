package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.EventProcessor;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.protocol.SdkVersion;
import io.sentry.spring.SentryExceptionResolver;
import io.sentry.spring.SentryUserProvider;
import io.sentry.spring.SentryUserProviderEventProcessor;
import io.sentry.spring.SentryWebConfiguration;
import io.sentry.transport.ITransport;
import io.sentry.transport.ITransportGate;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(name = "sentry.dsn")
@Open
public class SentryAutoConfiguration {

  /** Registers general purpose Sentry related beans. */
  @Configuration
  @EnableConfigurationProperties(SentryProperties.class)
  @Open
  static class HubConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public @NotNull Sentry.OptionsConfiguration<SentryOptions> optionsOptionsConfiguration(
        final @NotNull ObjectProvider<SentryOptions.BeforeSendCallback> beforeSendCallback,
        final @NotNull ObjectProvider<SentryOptions.BeforeBreadcrumbCallback>
                beforeBreadcrumbCallback,
        final @NotNull List<EventProcessor> eventProcessors,
        final @NotNull List<Integration> integrations,
        final @NotNull ObjectProvider<ITransportGate> transportGate,
        final @NotNull List<SentryUserProvider> sentryUserProviders,
        final @NotNull ObjectProvider<ITransport> transport,
        final @NotNull InAppIncludesResolver inAppPackagesResolver) {
      return options -> {
        beforeSendCallback.ifAvailable(options::setBeforeSend);
        beforeBreadcrumbCallback.ifAvailable(options::setBeforeBreadcrumb);
        eventProcessors.forEach(options::addEventProcessor);
        integrations.forEach(options::addIntegration);
        sentryUserProviders.forEach(
            sentryUserProvider ->
                options.addEventProcessor(
                    new SentryUserProviderEventProcessor(sentryUserProvider)));
        transportGate.ifAvailable(options::setTransportGate);
        transport.ifAvailable(options::setTransport);
        inAppPackagesResolver.resolveInAppIncludes().forEach(options::addInAppInclude);
      };
    }

    @Bean
    public @NotNull InAppIncludesResolver inAppPackagesResolver() {
      return new InAppIncludesResolver();
    }

    @Bean
    public @NotNull IHub sentryHub(
        final @NotNull Sentry.OptionsConfiguration<SentryOptions> optionsConfiguration,
        final @NotNull SentryProperties options,
        final @NotNull ObjectProvider<GitProperties> gitProperties) {
      optionsConfiguration.configure(options);
      gitProperties.ifAvailable(
          git -> {
            if (options.getRelease() == null && options.isUseGitCommitIdAsRelease()) {
              options.setRelease(git.getCommitId());
            }
          });

      options.setSentryClientName(BuildConfig.SENTRY_SPRING_BOOT_SDK_NAME);
      options.setSdkVersion(createSdkVersion(options));
      Sentry.init(options);
      return HubAdapter.getInstance();
    }

    /** Registers beans specific to Spring MVC. */
    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @Import(SentryWebConfiguration.class)
    @Open
    static class SentryWebMvcConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public @NotNull SentryExceptionResolver sentryExceptionResolver(
            final @NotNull IHub sentryHub,
            final @NotNull SentryProperties options) {
          return new SentryExceptionResolver(sentryHub, options.getExceptionResolverOrder());
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
