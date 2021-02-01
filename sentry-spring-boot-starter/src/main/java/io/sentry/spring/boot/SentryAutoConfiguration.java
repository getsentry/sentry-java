package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.EventProcessor;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ITransportFactory;
import io.sentry.Integration;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.protocol.SdkVersion;
import io.sentry.spring.SentryExceptionResolver;
import io.sentry.spring.SentryRequestResolver;
import io.sentry.spring.SentrySpringRequestListener;
import io.sentry.spring.SentryUserProvider;
import io.sentry.spring.SentryUserProviderEventProcessor;
import io.sentry.spring.SentryWebConfiguration;
import io.sentry.spring.tracing.SentrySpan;
import io.sentry.spring.tracing.SentrySpanAdvice;
import io.sentry.spring.tracing.SentryTracingFilter;
import io.sentry.spring.tracing.SentryTransaction;
import io.sentry.spring.tracing.SentryTransactionAdvice;
import io.sentry.transport.ITransportGate;
import io.sentry.transport.apache.ApacheHttpClientTransportFactory;
import java.util.List;
import org.aopalliance.aop.Advice;
import org.aspectj.lang.ProceedingJoinPoint;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.client.RestTemplate;

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
    @ConditionalOnMissingBean(name = "sentryOptionsConfiguration")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public @NotNull Sentry.OptionsConfiguration<SentryOptions> sentryOptionsConfiguration(
        final @NotNull ObjectProvider<SentryOptions.BeforeSendCallback> beforeSendCallback,
        final @NotNull ObjectProvider<SentryOptions.BeforeBreadcrumbCallback>
                beforeBreadcrumbCallback,
        final @NotNull ObjectProvider<SentryOptions.TracesSamplerCallback> tracesSamplerCallback,
        final @NotNull List<EventProcessor> eventProcessors,
        final @NotNull List<Integration> integrations,
        final @NotNull ObjectProvider<ITransportGate> transportGate,
        final @NotNull List<SentryUserProvider> sentryUserProviders,
        final @NotNull ObjectProvider<ITransportFactory> transportFactory,
        final @NotNull InAppIncludesResolver inAppPackagesResolver) {
      return options -> {
        beforeSendCallback.ifAvailable(options::setBeforeSend);
        beforeBreadcrumbCallback.ifAvailable(options::setBeforeBreadcrumb);
        tracesSamplerCallback.ifAvailable(options::setTracesSampler);
        eventProcessors.forEach(options::addEventProcessor);
        integrations.forEach(options::addIntegration);
        sentryUserProviders.forEach(
            sentryUserProvider ->
                options.addEventProcessor(
                    new SentryUserProviderEventProcessor(options, sentryUserProvider)));
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
      public @NotNull SentryRequestResolver sentryRequestResolver(final @NotNull IHub hub) {
        return new SentryRequestResolver(hub);
      }

      @Bean
      public @NotNull SentrySpringRequestListener sentrySpringRequestListener(
          final @NotNull IHub sentryHub, final @NotNull SentryRequestResolver requestResolver) {
        return new SentrySpringRequestListener(sentryHub, requestResolver);
      }

      @Bean
      @ConditionalOnMissingBean
      public @NotNull SentryExceptionResolver sentryExceptionResolver(
          final @NotNull IHub sentryHub, final @NotNull SentryProperties options) {
        return new SentryExceptionResolver(sentryHub, options.getExceptionResolverOrder());
      }

      @Bean
      @ConditionalOnProperty(name = "sentry.enable-tracing", havingValue = "true")
      @ConditionalOnMissingBean(name = "sentryTracingFilter")
      public FilterRegistrationBean<SentryTracingFilter> sentryTracingFilter(
          final @NotNull IHub hub, final @NotNull SentryRequestResolver sentryRequestResolver) {
        FilterRegistrationBean<SentryTracingFilter> filter =
            new FilterRegistrationBean<>(new SentryTracingFilter(hub, sentryRequestResolver));
        filter.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filter;
      }
    }

    @Configuration
    @ConditionalOnProperty(name = "sentry.enable-tracing", havingValue = "true")
    @ConditionalOnClass(ProceedingJoinPoint.class)
    @Open
    static class SentryPerformanceAspectsConfiguration {

      /**
       * Pointcut around which transactions are created.
       *
       * <p>This bean is can be replaced with user defined pointcut by specifying a {@link Pointcut}
       * bean with name "sentryTransactionPointcut".
       *
       * @return pointcut used by {@link SentryTransactionAdvice}.
       */
      @Bean
      @ConditionalOnMissingBean(name = "sentryTransactionPointcut")
      public @NotNull Pointcut sentryTransactionPointcut() {
        return new AnnotationMatchingPointcut(null, SentryTransaction.class);
      }

      @Bean
      public @NotNull Advice sentryTransactionAdvice(final @NotNull IHub hub) {
        return new SentryTransactionAdvice(hub);
      }

      @Bean
      public @NotNull Advisor sentryTransactionAdvisor(
          final @NotNull IHub hub,
          final @NotNull @Qualifier("sentryTransactionPointcut") Pointcut
                  sentryTransactionPointcut) {
        return new DefaultPointcutAdvisor(sentryTransactionPointcut, sentryTransactionAdvice(hub));
      }

      /**
       * Pointcut around which spans are created.
       *
       * <p>This bean is can be replaced with user defined pointcut by specifying a {@link Pointcut}
       * bean with name "sentrySpanPointcut".
       *
       * @return pointcut used by {@link SentrySpanAdvice}.
       */
      @Bean
      @ConditionalOnMissingBean(name = "sentrySpanPointcut")
      public @NotNull Pointcut sentrySpanPointcut() {
        return new AnnotationMatchingPointcut(null, SentrySpan.class);
      }

      @Bean
      public @NotNull Advice sentrySpanAdvice(final @NotNull IHub hub) {
        return new SentrySpanAdvice(hub);
      }

      @Bean
      public @NotNull Advisor sentrySpanAdvisor(
          IHub hub, final @NotNull @Qualifier("sentrySpanPointcut") Pointcut sentrySpanPointcut) {
        return new DefaultPointcutAdvisor(sentrySpanPointcut, sentrySpanAdvice(hub));
      }
    }

    @Configuration
    @AutoConfigureBefore(RestTemplateAutoConfiguration.class)
    @ConditionalOnClass(RestTemplate.class)
    @Open
    static class SentryPerformanceRestTemplateConfiguration {
      @Bean
      public SentrySpanRestTemplateCustomizer sentrySpanRestTemplateCustomizer(IHub hub) {
        return new SentrySpanRestTemplateCustomizer(hub);
      }
    }

    @Configuration
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

      final String name = BuildConfig.SENTRY_SPRING_BOOT_SDK_NAME;
      final String version = BuildConfig.VERSION_NAME;
      sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

      sdkVersion.addPackage("maven:sentry-spring-boot-starter", version);

      return sdkVersion;
    }
  }
}
