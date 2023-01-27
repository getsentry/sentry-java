package io.sentry.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.EventProcessor;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ITransportFactory;
import io.sentry.Integration;
import io.sentry.IntegrationName;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.opentelemetry.OpenTelemetryLinkErrorEventProcessor;
import io.sentry.protocol.SdkVersion;
import io.sentry.spring.jakarta.ContextTagsEventProcessor;
import io.sentry.spring.jakarta.SentryExceptionResolver;
import io.sentry.spring.jakarta.SentryRequestResolver;
import io.sentry.spring.jakarta.SentrySpringFilter;
import io.sentry.spring.jakarta.SentryUserFilter;
import io.sentry.spring.jakarta.SentryUserProvider;
import io.sentry.spring.jakarta.SentryWebConfiguration;
import io.sentry.spring.jakarta.SpringSecuritySentryUserProvider;
import io.sentry.spring.jakarta.tracing.SentryAdviceConfiguration;
import io.sentry.spring.jakarta.tracing.SentrySpanPointcutConfiguration;
import io.sentry.spring.jakarta.tracing.SentryTracingFilter;
import io.sentry.spring.jakarta.tracing.SentryTransactionPointcutConfiguration;
import io.sentry.spring.jakarta.tracing.SpringMvcTransactionNameProvider;
import io.sentry.spring.jakarta.tracing.TransactionNameProvider;
import io.sentry.transport.ITransportGate;
import io.sentry.transport.apache.ApacheHttpClientTransportFactory;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sentry.dsn")
@Open
public class SentryAutoConfiguration implements IntegrationName {

  /** Registers general purpose Sentry related beans. */
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SentryProperties.class)
  @Open
  static class HubConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "sentryOptionsConfiguration")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public @NotNull Sentry.OptionsConfiguration<SentryOptions> sentryOptionsConfiguration(
        final @NotNull ObjectProvider<SentryOptions.BeforeSendCallback> beforeSendCallback,
        final @NotNull ObjectProvider<SentryOptions.BeforeSendTransactionCallback> beforeSendTransactionCallback,
        final @NotNull ObjectProvider<SentryOptions.BeforeBreadcrumbCallback>
                beforeBreadcrumbCallback,
        final @NotNull ObjectProvider<SentryOptions.TracesSamplerCallback> tracesSamplerCallback,
        final @NotNull List<EventProcessor> eventProcessors,
        final @NotNull List<Integration> integrations,
        final @NotNull ObjectProvider<ITransportGate> transportGate,
        final @NotNull ObjectProvider<ITransportFactory> transportFactory,
        final @NotNull InAppIncludesResolver inAppPackagesResolver) {
      return options -> {
        beforeSendCallback.ifAvailable(options::setBeforeSend);
        beforeSendTransactionCallback.ifAvailable(options::setBeforeSendTransaction);
        beforeBreadcrumbCallback.ifAvailable(options::setBeforeBreadcrumb);
        tracesSamplerCallback.ifAvailable(options::setTracesSampler);
        eventProcessors.forEach(options::addEventProcessor);
        integrations.forEach(options::addIntegration);
        transportGate.ifAvailable(options::setTransportGate);
        transportFactory.ifAvailable(options::setTransportFactory);
        inAppPackagesResolver.resolveInAppIncludes().forEach(options::addInAppInclude);
      };
    }

    @Bean
    public @NotNull InAppIncludesResolver inAppPackagesResolver() {
      return new InAppIncludesResolver();
    }

    @Bean
    public @NotNull IHub sentryHub(
        final @NotNull List<Sentry.OptionsConfiguration<SentryOptions>> optionsConfigurations,
        final @NotNull SentryProperties options,
        final @NotNull ObjectProvider<GitProperties> gitProperties) {
      optionsConfigurations.forEach(
          optionsConfiguration -> optionsConfiguration.configure(options));
      gitProperties.ifAvailable(
          git -> {
            if (options.getRelease() == null && options.isUseGitCommitIdAsRelease()) {
              options.setRelease(git.getCommitId());
            }
          });

      options.setSentryClientName(BuildConfig.SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME);
      options.setSdkVersion(createSdkVersion(options));
      if (options.getTracesSampleRate() == null) {
        options.setTracesSampleRate(0.0);
      }
      // Spring Boot sets ignored exceptions in runtime using reflection - where the generic
      // information is lost
      // its technically possible to set non-throwable class to `ignoredExceptionsForType` set
      // here we make sure that only classes that extend throwable are set on this field
      options.getIgnoredExceptionsForType().removeIf(it -> !Throwable.class.isAssignableFrom(it));
      Sentry.init(options);
      return HubAdapter.getInstance();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MDC.class)
    @Open
    static class ContextTagsEventProcessorConfiguration {

      @Bean
      public @NotNull ContextTagsEventProcessor contextTagsEventProcessor(
          final @NotNull SentryOptions sentryOptions) {
        return new ContextTagsEventProcessor(sentryOptions);
      }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "sentry.auto-init", havingValue = "false")
    @ConditionalOnClass(OpenTelemetryLinkErrorEventProcessor.class)
    @Open
    static class OpenTelemetryLinkErrorEventProcessorConfiguration {

      @Bean
      @ConditionalOnMissingBean
      public @NotNull OpenTelemetryLinkErrorEventProcessor openTelemetryLinkErrorEventProcessor() {
        return new OpenTelemetryLinkErrorEventProcessor();
      }
    }

    /** Registers beans specific to Spring MVC. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @Import(SentryWebConfiguration.class)
    @Open
    static class SentryWebMvcConfiguration {

      private static final int SENTRY_SPRING_FILTER_PRECEDENCE = Ordered.HIGHEST_PRECEDENCE;

      @Configuration(proxyBeanMethods = false)
      @ConditionalOnClass(SecurityContextHolder.class)
      @Open
      static class SentrySecurityConfiguration {

        /**
         * Configures {@link SpringSecuritySentryUserProvider} only if Spring Security is on the
         * classpath. Its order is set to be higher than {@link
         * SentryWebConfiguration#httpServletRequestSentryUserProvider(SentryOptions)}
         *
         * @param sentryOptions the Sentry options
         * @return {@link SpringSecuritySentryUserProvider}
         */
        @Bean
        @Order(1)
        public @NotNull SpringSecuritySentryUserProvider springSecuritySentryUserProvider(
            final @NotNull SentryOptions sentryOptions) {
          return new SpringSecuritySentryUserProvider(sentryOptions);
        }
      }

      /**
       * Configures {@link SentryUserFilter}. By default it runs as the last filter in order to make
       * sure that all potential authentication information is propagated to {@link
       * HttpServletRequest#getUserPrincipal()}. If Spring Security is auto-configured, its order is
       * set to run after Spring Security.
       *
       * @param hub the Sentry hub
       * @param sentryProperties the Sentry properties
       * @param sentryUserProvider the user provider
       * @return {@link SentryUserFilter} registration bean
       */
      @Bean
      @ConditionalOnBean(SentryUserProvider.class)
      public @NotNull FilterRegistrationBean<SentryUserFilter> sentryUserFilter(
          final @NotNull IHub hub,
          final @NotNull SentryProperties sentryProperties,
          final @NotNull List<SentryUserProvider> sentryUserProvider) {
        final FilterRegistrationBean<SentryUserFilter> filter = new FilterRegistrationBean<>();
        filter.setFilter(new SentryUserFilter(hub, sentryUserProvider));
        filter.setOrder(resolveUserFilterOrder(sentryProperties));
        return filter;
      }

      private @NotNull Integer resolveUserFilterOrder(
          final @NotNull SentryProperties sentryProperties) {
        return Optional.ofNullable(sentryProperties.getUserFilterOrder())
            .orElse(Ordered.LOWEST_PRECEDENCE);
      }

      @Bean
      public @NotNull SentryRequestResolver sentryRequestResolver(final @NotNull IHub hub) {
        return new SentryRequestResolver(hub);
      }

      @Bean
      @ConditionalOnMissingBean(TransactionNameProvider.class)
      public @NotNull TransactionNameProvider transactionNameProvider() {
        return new SpringMvcTransactionNameProvider();
      }

      @Bean
      @ConditionalOnMissingBean(name = "sentrySpringFilter")
      public @NotNull FilterRegistrationBean<SentrySpringFilter> sentrySpringFilter(
          final @NotNull IHub hub,
          final @NotNull SentryRequestResolver requestResolver,
          final @NotNull TransactionNameProvider transactionNameProvider) {
        FilterRegistrationBean<SentrySpringFilter> filter =
            new FilterRegistrationBean<>(
                new SentrySpringFilter(hub, requestResolver, transactionNameProvider));
        filter.setOrder(SENTRY_SPRING_FILTER_PRECEDENCE);
        return filter;
      }

      @Bean
      @Conditional(SentryTracingCondition.class)
      @ConditionalOnMissingBean(name = "sentryTracingFilter")
      public FilterRegistrationBean<SentryTracingFilter> sentryTracingFilter(
          final @NotNull IHub hub, final @NotNull TransactionNameProvider transactionNameProvider) {
        FilterRegistrationBean<SentryTracingFilter> filter =
            new FilterRegistrationBean<>(new SentryTracingFilter(hub, transactionNameProvider));
        filter.setOrder(SENTRY_SPRING_FILTER_PRECEDENCE + 1); // must run after SentrySpringFilter
        return filter;
      }

      @Bean
      @ConditionalOnMissingBean
      @ConditionalOnClass(HandlerExceptionResolver.class)
      public @NotNull SentryExceptionResolver sentryExceptionResolver(
          final @NotNull IHub sentryHub,
          final @NotNull TransactionNameProvider transactionNameProvider,
          final @NotNull SentryProperties options) {
        return new SentryExceptionResolver(
            sentryHub, transactionNameProvider, options.getExceptionResolverOrder());
      }
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(SentryTracingCondition.class)
    @ConditionalOnClass(ProceedingJoinPoint.class)
    @Import(SentryAdviceConfiguration.class)
    @Open
    static class SentryPerformanceAspectsConfiguration {

      @Configuration(proxyBeanMethods = false)
      @ConditionalOnMissingBean(name = "sentryTransactionPointcut")
      @Import(SentryTransactionPointcutConfiguration.class)
      @Open
      static class SentryTransactionPointcutAutoConfiguration {}

      @Configuration(proxyBeanMethods = false)
      @ConditionalOnMissingBean(name = "sentrySpanPointcut")
      @Import(SentrySpanPointcutConfiguration.class)
      @Open
      static class SentrySpanPointcutAutoConfiguration {}
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureBefore(RestTemplateAutoConfiguration.class)
    @ConditionalOnClass(RestTemplate.class)
    @Open
    static class SentryPerformanceRestTemplateConfiguration {
      @Bean
      public SentrySpanRestTemplateCustomizer sentrySpanRestTemplateCustomizer(IHub hub) {
        return new SentrySpanRestTemplateCustomizer(hub);
      }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureBefore(WebClientAutoConfiguration.class)
    @ConditionalOnClass(WebClient.class)
    @Open
    static class SentryPerformanceWebClientConfiguration {
      @Bean
      public SentrySpanWebClientCustomizer sentrySpanWebClientCustomizer(IHub hub) {
        return new SentrySpanWebClientCustomizer(hub);
      }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(ITransportFactory.class)
    @ConditionalOnClass(ApacheHttpClientTransportFactory.class)
    @Open
    static class ApacheHttpClientTransportFactoryAutoconfiguration {

      @Bean
      public @NotNull ApacheHttpClientTransportFactory apacheHttpClientTransportFactory() {
        return new ApacheHttpClientTransportFactory();
      }
    }

    private static @NotNull SdkVersion createSdkVersion(
        final @NotNull SentryOptions sentryOptions) {
      SdkVersion sdkVersion = sentryOptions.getSdkVersion();

      final String name = BuildConfig.SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME;
      final String version = BuildConfig.VERSION_NAME;
      sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

      sdkVersion.addPackage("maven:io.sentry:sentry-spring-boot-starter-jakarta", version);
      sdkVersion.addIntegration("SpringBoot3");

      return sdkVersion;
    }
  }

  static final class SentryTracingCondition extends AnyNestedCondition {

    public SentryTracingCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(name = "sentry.traces-sample-rate")
    @SuppressWarnings("UnusedNestedClass")
    private static class SentryTracesSampleRateCondition {}

    @ConditionalOnBean(SentryOptions.TracesSamplerCallback.class)
    @SuppressWarnings("UnusedNestedClass")
    private static class SentryTracesSamplerBeanCondition {}
  }
}
