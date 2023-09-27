package io.sentry.spring.checkin;

import com.jakewharton.nopen.annotation.Open;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AOP pointcut configuration for {@link SentryCheckIn}. */
@Configuration(proxyBeanMethods = false)
@Open
@ApiStatus.Experimental
public class SentryCheckInPointcutConfiguration {

  /**
   * Pointcut around which check-ins are created.
   *
   * @return pointcut used by {@link SentryCheckInAdvice}.
   */
  @Bean
  public @NotNull Pointcut sentryCheckInPointcut() {
    return new ComposablePointcut(new AnnotationClassFilter(SentryCheckIn.class, true))
        .union(new AnnotationMatchingPointcut(null, SentryCheckIn.class));
  }
}
