package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import org.aopalliance.aop.Advice;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides infrastructure beans for creating transactions and spans around bean methods annotated with {@link SentryTransaction} and {@link SentrySpan}.
 */
@Configuration
@Open
public class SentryTracingConfiguration {

  @Bean
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
  public @NotNull Pointcut sentrySpanPointcut() {
    return new AnnotationMatchingPointcut(null, SentrySpan.class);
  }

  @Bean
  public @NotNull Advice sentrySpanAdvice(final @NotNull IHub hub) {
    return new SentrySpanAdvice(hub);
  }

  @Bean
  public @NotNull Advisor sentrySpanAdvisor(final IHub hub, final @NotNull @Qualifier("sentrySpanPointcut") Pointcut sentrySpanPointcut) {
    return new DefaultPointcutAdvisor(sentrySpanPointcut, sentrySpanAdvice(hub));
  }
}
