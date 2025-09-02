package io.sentry.spring7.tracing;

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

/** AOP pointcut configuration for {@link SentryTransaction}. */
@Configuration(proxyBeanMethods = false)
@Open
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class SentryTransactionPointcutConfiguration {

  /**
   * Pointcut around which transactions are created.
   *
   * @return pointcut used by {@link SentryTransactionAdvice}.
   */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Pointcut sentryTransactionPointcut() {
    return new ComposablePointcut(new AnnotationClassFilter(SentryTransaction.class, true))
        .union(new AnnotationMatchingPointcut(null, SentryTransaction.class));
  }
}
