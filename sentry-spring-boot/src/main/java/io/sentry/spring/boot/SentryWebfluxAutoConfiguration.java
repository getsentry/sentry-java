package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScope;
import io.sentry.IScopes;
import io.sentry.spring.webflux.SentryScheduleHook;
import io.sentry.spring.webflux.SentryWebExceptionHandler;
import io.sentry.spring.webflux.SentryWebFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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

  /** Configures exception handler that handles unhandled exceptions and sends them to Sentry. */
  @Bean
  public @NotNull SentryWebExceptionHandler sentryWebExceptionHandler(
      final @NotNull IScopes scopes) {
    return new SentryWebExceptionHandler(scopes);
  }
}
