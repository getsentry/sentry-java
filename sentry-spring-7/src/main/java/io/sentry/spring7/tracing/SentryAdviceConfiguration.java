package io.sentry.spring7.tracing;

import com.jakewharton.nopen.annotation.Open;
import org.aopalliance.aop.Advice;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/** Creates advice infrastructure for {@link SentrySpan} and {@link SentryTransaction}. */
@Configuration(proxyBeanMethods = false)
@Open
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class SentryAdviceConfiguration {

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advice sentryTransactionAdvice() {
    return new SentryTransactionAdvice();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advisor sentryTransactionAdvisor(
      final @NotNull @Qualifier("sentryTransactionPointcut") Pointcut sentryTransactionPointcut,
      final @NotNull @Qualifier("sentryTransactionAdvice") Advice sentryTransactionAdvice) {
    return new DefaultPointcutAdvisor(sentryTransactionPointcut, sentryTransactionAdvice);
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advice sentrySpanAdvice() {
    return new SentrySpanAdvice();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advisor sentrySpanAdvisor(
      final @NotNull @Qualifier("sentrySpanPointcut") Pointcut sentrySpanPointcut,
      final @NotNull @Qualifier("sentrySpanAdvice") Advice sentrySpanAdvice) {
    return new DefaultPointcutAdvisor(sentrySpanPointcut, sentrySpanAdvice);
  }
}
