package io.sentry.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import graphql.GraphQLError;
import io.sentry.EventProcessor;
import io.sentry.IScopes;
import io.sentry.ISpanFactory;
import io.sentry.ITransportFactory;
import io.sentry.InitPriority;
import io.sentry.Integration;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryOptions;
import io.sentry.protocol.SdkVersion;
import io.sentry.quartz.SentryJobListener;
import io.sentry.spring.boot.jakarta.graphql.SentryGraphql22AutoConfiguration;
import io.sentry.spring.boot.jakarta.graphql.SentryGraphqlAutoConfiguration;
import io.sentry.spring.jakarta.ContextTagsEventProcessor;
import io.sentry.spring.jakarta.SentryExceptionResolver;
import io.sentry.spring.jakarta.SentryRequestResolver;
import io.sentry.spring.jakarta.SentrySpringFilter;
import io.sentry.spring.jakarta.SentryUserFilter;
import io.sentry.spring.jakarta.SentryUserProvider;
import io.sentry.spring.jakarta.SentryWebConfiguration;
import io.sentry.spring.jakarta.SpringProfilesEventProcessor;
import io.sentry.spring.jakarta.SpringSecuritySentryUserProvider;
import io.sentry.spring.jakarta.checkin.SentryCheckInAdviceConfiguration;
import io.sentry.spring.jakarta.checkin.SentryCheckInPointcutConfiguration;
import io.sentry.spring.jakarta.checkin.SentryQuartzConfiguration;
import io.sentry.spring.jakarta.exception.SentryCaptureExceptionParameterPointcutConfiguration;
import io.sentry.spring.jakarta.exception.SentryExceptionParameterAdviceConfiguration;
import io.sentry.spring.jakarta.opentelemetry.SentryOpenTelemetryAgentWithoutAutoInitConfiguration;
import io.sentry.spring.jakarta.opentelemetry.SentryOpenTelemetryNoAgentConfiguration;
import io.sentry.spring.jakarta.tracing.SentryAdviceConfiguration;
import io.sentry.spring.jakarta.tracing.SentrySpanPointcutConfiguration;
import io.sentry.spring.jakarta.tracing.SentryTracingFilter;
import io.sentry.spring.jakarta.tracing.SentryTransactionPointcutConfiguration;
import io.sentry.spring.jakarta.tracing.SpringMvcTransactionNameProvider;
import io.sentry.spring.jakarta.tracing.SpringServletTransactionNameProvider;
import io.sentry.spring.jakarta.tracing.TransactionNameProvider;
import io.sentry.transport.ITransportGate;
import io.sentry.transport.apache.ApacheHttpClientTransportFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.jetbrains.annotations.NotNull;
import org.quartz.core.QuartzScheduler;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
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
import org.springframework.core.env.Environment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sentry.dsn")
@Open
public class SentryAutoConfiguration {

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
        final @NotNull ObjectProvider<SentryOptions.BeforeSendTransactionCallback>
                beforeSendTransactionCallback,
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

    @Configuration(proxyBeanMethods = false)
    @Import(SentryOpenTelemetryAgentWithoutAutoInitConfiguration.class)
    @Open
    @ConditionalOnProperty(name = "sentry.auto-init", havingValue = "false")
    @ConditionalOnClass(name = {"io.sentry.opentelemetry.agent.AgentMarker"})
    static class OpenTelemetryAgentWithoutAutoInitConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @Import(SentryOpenTelemetryNoAgentConfiguration.class)
    @Open
    @ConditionalOnClass(
        name = {
          "io.opentelemetry.api.OpenTelemetry",
          "io.sentry.opentelemetry.SentryAutoConfigurationCustomizerProvider"
        })
    @ConditionalOnMissingClass("io.sentry.opentelemetry.agent.AgentMarker")
    static class OpenTelemetryNoAgentConfiguration {}

    @Bean
    public @NotNull IScopes sentryHub(
        final @NotNull List<Sentry.OptionsConfiguration<SentryOptions>> optionsConfigurations,
        final @NotNull SentryProperties options,
        final @NotNull ObjectProvider<ISpanFactory> spanFactory,
        final @NotNull ObjectProvider<GitProperties> gitProperties) {
      optionsConfigurations.forEach(
          optionsConfiguration -> optionsConfiguration.configure(options));
      gitProperties.ifAvailable(
          git -> {
            if (options.getRelease() == null && options.isUseGitCommitIdAsRelease()) {
              options.setRelease(git.getCommitId());
            }
          });
      spanFactory.ifAvailable(options::setSpanFactory);

      options.setSentryClientName(
          BuildConfig.SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME + "/" + BuildConfig.VERSION_NAME);
      options.setSdkVersion(createSdkVersion(options));
      options.setInitPriority(InitPriority.LOW);
      addPackageAndIntegrationInfo();
      // Spring Boot sets ignored exceptions in runtime using reflection - where the generic
      // information is lost
      // its technically possible to set non-throwable class to `ignoredExceptionsForType` set
      // here we make sure that only classes that extend throwable are set on this field
      options.getIgnoredExceptionsForType().removeIf(it -> !Throwable.class.isAssignableFrom(it));
      Sentry.init(options);
      return ScopesAdapter.getInstance();
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
    @Import(SentryGraphqlAutoConfiguration.class)
    @Open
    @ConditionalOnClass({
      io.sentry.graphql.SentryInstrumentation.class,
      DataFetcherExceptionResolverAdapter.class,
      GraphQLError.class
    })
    @ConditionalOnMissingClass({
      "io.sentry.graphql22.SentryInstrumentation" // avoid duplicate bean
    })
    static class GraphqlConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @Import(SentryGraphql22AutoConfiguration.class)
    @Open
    @ConditionalOnClass({
      io.sentry.graphql22.SentryInstrumentation.class,
      DataFetcherExceptionResolverAdapter.class,
      GraphQLError.class
    })
    static class Graphql22Configuration {}

    @Configuration(proxyBeanMethods = false)
    @Import(SentryQuartzConfiguration.class)
    @Open
    @ConditionalOnClass({
      SentryJobListener.class,
      QuartzScheduler.class,
      SchedulerFactoryBean.class
    })
    static class QuartzConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ProceedingJoinPoint.class)
    @ConditionalOnProperty(
        value = "sentry.enable-aot-compatibility",
        havingValue = "false",
        matchIfMissing = true)
    @Import(SentryCheckInAdviceConfiguration.class)
    @Open
    static class SentryCheckInAspectsConfiguration {
      @Configuration(proxyBeanMethods = false)
      @ConditionalOnMissingBean(name = "sentryCheckInPointcut")
      @Import(SentryCheckInPointcutConfiguration.class)
      @Open
      static class SentryCheckInPointcutAutoConfiguration {}
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
       * @param scopes the Sentry scopes
       * @param sentryProperties the Sentry properties
       * @param sentryUserProvider the user provider
       * @return {@link SentryUserFilter} registration bean
       */
      @Bean
      @ConditionalOnBean(SentryUserProvider.class)
      public @NotNull FilterRegistrationBean<SentryUserFilter> sentryUserFilter(
          final @NotNull IScopes scopes,
          final @NotNull SentryProperties sentryProperties,
          final @NotNull List<SentryUserProvider> sentryUserProvider) {
        final FilterRegistrationBean<SentryUserFilter> filter = new FilterRegistrationBean<>();
        filter.setFilter(new SentryUserFilter(scopes, sentryUserProvider));
        filter.setOrder(resolveUserFilterOrder(sentryProperties));
        return filter;
      }

      private @NotNull Integer resolveUserFilterOrder(
          final @NotNull SentryProperties sentryProperties) {
        return Optional.ofNullable(sentryProperties.getUserFilterOrder())
            .orElse(Ordered.LOWEST_PRECEDENCE);
      }

      @Bean
      public @NotNull SentryRequestResolver sentryRequestResolver(final @NotNull IScopes scopes) {
        return new SentryRequestResolver(scopes);
      }

      @Bean
      @ConditionalOnMissingBean(name = "sentrySpringFilter")
      public @NotNull FilterRegistrationBean<SentrySpringFilter> sentrySpringFilter(
          final @NotNull IScopes scopes,
          final @NotNull SentryRequestResolver requestResolver,
          final @NotNull TransactionNameProvider transactionNameProvider) {
        FilterRegistrationBean<SentrySpringFilter> filter =
            new FilterRegistrationBean<>(
                new SentrySpringFilter(scopes, requestResolver, transactionNameProvider));
        filter.setOrder(SENTRY_SPRING_FILTER_PRECEDENCE);
        return filter;
      }

      @Bean
      @ConditionalOnMissingBean(name = "sentryTracingFilter")
      public FilterRegistrationBean<SentryTracingFilter> sentryTracingFilter(
          final @NotNull IScopes scopes,
          final @NotNull TransactionNameProvider transactionNameProvider,
          final @NotNull SentryProperties sentryProperties) {
        FilterRegistrationBean<SentryTracingFilter> filter =
            new FilterRegistrationBean<>(
                new SentryTracingFilter(
                    scopes,
                    transactionNameProvider,
                    sentryProperties.isKeepTransactionsOpenForAsyncResponses()));
        filter.setOrder(SENTRY_SPRING_FILTER_PRECEDENCE + 1); // must run after SentrySpringFilter
        return filter;
      }

      @Configuration(proxyBeanMethods = false)
      @ConditionalOnClass(HandlerExceptionResolver.class)
      @Open
      static class SentryMvcModeConfig {

        @Bean
        @ConditionalOnMissingBean
        public @NotNull SentryExceptionResolver sentryExceptionResolver(
            final @NotNull IScopes scopes,
            final @NotNull TransactionNameProvider transactionNameProvider,
            final @NotNull SentryProperties options) {
          return new SentryExceptionResolver(
              scopes, transactionNameProvider, options.getExceptionResolverOrder());
        }

        @Bean
        @ConditionalOnMissingBean(TransactionNameProvider.class)
        public @NotNull TransactionNameProvider transactionNameProvider() {
          return new SpringMvcTransactionNameProvider();
        }
      }

      @Configuration(proxyBeanMethods = false)
      @ConditionalOnMissingClass("org.springframework.web.servlet.HandlerExceptionResolver")
      @Open
      static class SentryServletModeConfig {

        @Bean
        @ConditionalOnMissingBean(TransactionNameProvider.class)
        public @NotNull TransactionNameProvider transactionNameProvider() {
          return new SpringServletTransactionNameProvider();
        }
      }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ProceedingJoinPoint.class)
    @ConditionalOnProperty(
        value = "sentry.enable-aot-compatibility",
        havingValue = "false",
        matchIfMissing = true)
    @Import(SentryExceptionParameterAdviceConfiguration.class)
    @Open
    static class SentryErrorAspectsConfiguration {
      @Configuration(proxyBeanMethods = false)
      @ConditionalOnMissingBean(name = "sentryCaptureExceptionParameterPointcut")
      @Import(SentryCaptureExceptionParameterPointcutConfiguration.class)
      @Open
      static class SentryCaptureExceptionParameterPointcutAutoConfiguration {}
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        value = "sentry.enable-aot-compatibility",
        havingValue = "false",
        matchIfMissing = true)
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
      public SentrySpanRestTemplateCustomizer sentrySpanRestTemplateCustomizer(IScopes scopes) {
        return new SentrySpanRestTemplateCustomizer(scopes);
      }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureBefore(RestClientAutoConfiguration.class)
    @ConditionalOnClass(RestClient.class)
    @Open
    static class SentrySpanRestClientConfiguration {
      @Bean
      public SentrySpanRestClientCustomizer sentrySpanRestClientCustomizer(IScopes scopes) {
        return new SentrySpanRestClientCustomizer(scopes);
      }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureBefore(WebClientAutoConfiguration.class)
    @ConditionalOnClass(WebClient.class)
    @Open
    static class SentryPerformanceWebClientConfiguration {
      @Bean
      public SentrySpanWebClientCustomizer sentrySpanWebClientCustomizer(IScopes scopes) {
        return new SentrySpanWebClientCustomizer(scopes);
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

      return sdkVersion;
    }

    private static void addPackageAndIntegrationInfo() {
      SentryIntegrationPackageStorage.getInstance()
          .addPackage(
              "maven:io.sentry:sentry-spring-boot-starter-jakarta", BuildConfig.VERSION_NAME);
      SentryIntegrationPackageStorage.getInstance().addIntegration("SpringBoot3");
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

  @Configuration(proxyBeanMethods = false)
  @Open
  static class SpringProfilesEventProcessorConfiguration {
    @Bean
    public @NotNull SpringProfilesEventProcessor springProfilesEventProcessor(
        final Environment environment) {
      return new SpringProfilesEventProcessor(environment);
    }
  }
}
