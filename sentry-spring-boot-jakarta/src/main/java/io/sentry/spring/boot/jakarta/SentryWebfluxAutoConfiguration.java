package io.sentry.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScope;
import io.sentry.IScopes;
import io.sentry.spring.jakarta.webflux.SentryScheduleHook;
import io.sentry.spring.jakarta.webflux.SentryWebExceptionHandler;
import io.sentry.spring.jakarta.webflux.SentryWebFilter;
import io.sentry.spring.jakarta.webflux.SentryWebFilterWithThreadLocalAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Schedulers;

/** Configures Sentry integration for Spring Webflux and Project Reactor. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnBean(IScopes.class)
@ConditionalOnClass(Schedulers.class)
@Open
@ApiStatus.Experimental
public class SentryWebfluxAutoConfiguration {
  private static final int SENTRY_SPRING_FILTER_PRECEDENCE = Ordered.HIGHEST_PRECEDENCE;

  @Configuration(proxyBeanMethods = false)
  @Conditional(SentryThreadLocalAccessorCondition.class)
  @Open
  static class SentryWebfluxFilterThreadLocalAccessorConfiguration {

    /**
     * Configures a filter that sets up Sentry {@link IScope} for each request.
     *
     * <p>Makes use of newer reactor-core and context-propagation library feature
     * ThreadLocalAccessor to propagate the Sentry scopes.
     */
    @Bean
    @Order(SENTRY_SPRING_FILTER_PRECEDENCE)
    public @NotNull SentryWebFilterWithThreadLocalAccessor sentryWebFilterWithContextPropagation(
        final @NotNull IScopes scopes) {
      Hooks.enableAutomaticContextPropagation();
      return new SentryWebFilterWithThreadLocalAccessor(scopes);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Conditional(SentryLegacyFilterConfigurationCondition.class)
  @Open
  static class SentryWebfluxFilterConfiguration {

    /** Configures hook that sets correct scopes on the executing thread. */
    @Bean
    public @NotNull ApplicationRunner sentryScheduleHookApplicationRunner() {
      return args -> {
        Schedulers.onScheduleHook("sentry", new SentryScheduleHook());
      };
    }

    /** Configures a filter that sets up Sentry {@link IScope} for each request. */
    @Bean
    @Order(SENTRY_SPRING_FILTER_PRECEDENCE)
    public @NotNull SentryWebFilter sentryWebFilter(final @NotNull IScopes scopes) {
      return new SentryWebFilter(scopes);
    }
  }

  /** Configures exception handler that handles unhandled exceptions and sends them to Sentry. */
  @Bean
  public @NotNull SentryWebExceptionHandler sentryWebExceptionHandler(
      final @NotNull IScopes scopes) {
    return new SentryWebExceptionHandler(scopes);
  }

  static final class SentryLegacyFilterConfigurationCondition extends AnyNestedCondition {

    public SentryLegacyFilterConfigurationCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(
        name = "sentry.reactive.thread-local-accessor-enabled",
        havingValue = "false",
        matchIfMissing = true)
    @SuppressWarnings("UnusedNestedClass")
    private static class SentryDisableThreadLocalAccessorCondition {}

    @ConditionalOnMissingClass("io.micrometer.context.ThreadLocalAccessor")
    @SuppressWarnings("UnusedNestedClass")
    private static class ThreadLocalAccessorClassCondition {}
  }

  static final class SentryThreadLocalAccessorCondition extends AllNestedConditions {

    public SentryThreadLocalAccessorCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(
        name = "sentry.reactive.thread-local-accessor-enabled",
        havingValue = "true")
    @SuppressWarnings("UnusedNestedClass")
    private static class SentryEnableThreadLocalAccessorCondition {}

    @ConditionalOnClass(io.micrometer.context.ThreadLocalAccessor.class)
    @SuppressWarnings("UnusedNestedClass")
    private static class ThreadLocalAccessorClassCondition {}
  }
}
