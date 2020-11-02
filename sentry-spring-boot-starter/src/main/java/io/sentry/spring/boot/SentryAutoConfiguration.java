package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.EventProcessor;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.protocol.SdkVersion;
import io.sentry.spring.SentryRequestResolver;
import io.sentry.spring.SentryUserProvider;
import io.sentry.spring.SentryUserProviderEventProcessor;
import io.sentry.spring.SentryWebConfiguration;
import io.sentry.spring.tracing.SentrySpan;
import io.sentry.spring.tracing.SentrySpanAdvice;
import io.sentry.spring.tracing.SentryTracingFilter;
import io.sentry.spring.tracing.SentryTransaction;
import io.sentry.spring.tracing.SentryTransactionAdvice;
import io.sentry.transport.ITransport;
import io.sentry.transport.ITransportGate;
import java.util.List;
import org.aopalliance.aop.Advice;
import org.aspectj.lang.ProceedingJoinPoint;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

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
        return new ComposablePointcut(new AnnotationMatchingPointcut(Component.class))
            .union(new AnnotationMatchingPointcut(Service.class))
            .union(new AnnotationMatchingPointcut(null, SentrySpan.class));
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
