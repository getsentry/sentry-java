package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
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
import reactor.core.scheduler.Schedulers;

/** Configures Sentry integration for Spring Webflux and Project Reactor. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnBean(IHub.class)
@ConditionalOnClass(Schedulers.class)
@Open
@ApiStatus.Experimental
public class SentryWebfluxAutoConfiguration {

  /** Configures hook that sets correct hub on the executing thread. */
  @Bean
  public @NotNull ApplicationRunner sentryScheduleHookApplicationRunner() {
    return args -> {
      Schedulers.onScheduleHook("sentry", new SentryScheduleHook());
    };
  }

  /** Configures a filter that sets up Sentry {@link io.sentry.Scope} for each request. */
  @Bean
  public @NotNull SentryWebFilter sentryWebFilter(final @NotNull IHub hub) {
    return new SentryWebFilter(hub);
  }

  /** Configures exception handler that handles unhandled exceptions and sends them to Sentry. */
  @Bean
  public @NotNull SentryWebExceptionHandler sentryWebExceptionHandler(final @NotNull IHub hub) {
    return new SentryWebExceptionHandler(hub);
  }
}
