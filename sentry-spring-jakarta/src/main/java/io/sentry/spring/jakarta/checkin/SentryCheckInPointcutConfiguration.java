package io.sentry.spring.jakarta.checkin;

import com.jakewharton.nopen.annotation.Open;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/** AOP pointcut configuration for {@link SentryCheckIn}. */
@Configuration(proxyBeanMethods = false)
@Open
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class SentryCheckInPointcutConfiguration {

  /**
   * Pointcut around which check-ins are created.
   *
   * @return pointcut used by {@link SentryCheckInAdvice}.
   */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Pointcut sentryCheckInPointcut() {
    return new ComposablePointcut(new AnnotationClassFilter(SentryCheckIn.class, true))
        .union(new AnnotationMatchingPointcut(null, SentryCheckIn.class));
  }
}
