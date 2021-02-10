package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import org.aopalliance.aop.Advice;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates advice infrastructure for {@link SentrySpan} and {@link SentryTransaction}. */
@Configuration
@Open
public class SentryAdviceConfiguration {

  @Bean
  public @NotNull Advice sentryTransactionAdvice(final @NotNull IHub hub) {
    return new SentryTransactionAdvice(hub);
  }

  @Bean
  public @NotNull Advisor sentryTransactionAdvisor(
      final @NotNull IHub hub,
      final @NotNull @Qualifier("sentryTransactionPointcut") Pointcut sentryTransactionPointcut) {
    return new DefaultPointcutAdvisor(sentryTransactionPointcut, sentryTransactionAdvice(hub));
  }

  @Bean
  public @NotNull Advice sentrySpanAdvice(final @NotNull IHub hub) {
    return new SentrySpanAdvice(hub);
  }

  @Bean
  public @NotNull Advisor sentrySpanAdvisor(
      final IHub hub, final @NotNull @Qualifier("sentrySpanPointcut") Pointcut sentrySpanPointcut) {
    return new DefaultPointcutAdvisor(sentrySpanPointcut, sentrySpanAdvice(hub));
  }
}
