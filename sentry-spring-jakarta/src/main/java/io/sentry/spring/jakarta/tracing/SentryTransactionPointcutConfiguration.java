package io.sentry.spring.jakarta.tracing;

import com.jakewharton.nopen.annotation.Open;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AOP pointcut configuration for {@link SentryTransaction}. */
@Configuration(proxyBeanMethods = false)
@Open
public class SentryTransactionPointcutConfiguration {

  /**
   * Pointcut around which transactions are created.
   *
   * @return pointcut used by {@link SentryTransactionAdvice}.
   */
  @Bean
  public @NotNull Pointcut sentryTransactionPointcut() {
    return new ComposablePointcut(new AnnotationClassFilter(SentryTransaction.class, true))
        .union(new AnnotationMatchingPointcut(null, SentryTransaction.class));
  }
}
