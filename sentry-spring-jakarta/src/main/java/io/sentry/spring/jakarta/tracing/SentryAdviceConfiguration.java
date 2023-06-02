package io.sentry.spring.jakarta.tracing;

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

/** Creates advice infrastructure for {@link SentrySpan} and {@link SentryTransaction} and {@link SentryCaptureException}. */
@Configuration(proxyBeanMethods = false)
@Open
public class SentryAdviceConfiguration {

  @Bean
  public @NotNull Advice sentryTransactionAdvice(final @NotNull IHub hub) {
    return new SentryTransactionAdvice(hub);
  }

  @Bean
  public @NotNull Advisor sentryTransactionAdvisor(
      final @NotNull @Qualifier("sentryTransactionPointcut") Pointcut sentryTransactionPointcut,
      final @NotNull @Qualifier("sentryTransactionAdvice") Advice sentryTransactionAdvice) {
    return new DefaultPointcutAdvisor(sentryTransactionPointcut, sentryTransactionAdvice);
  }

  @Bean
  public @NotNull Advice sentrySpanAdvice(final @NotNull IHub hub) {
    return new SentrySpanAdvice(hub);
  }

  @Bean
  public @NotNull Advisor sentrySpanAdvisor(
      final @NotNull @Qualifier("sentrySpanPointcut") Pointcut sentrySpanPointcut,
      final @NotNull @Qualifier("sentrySpanAdvice") Advice sentrySpanAdvice) {
    return new DefaultPointcutAdvisor(sentrySpanPointcut, sentrySpanAdvice);
  }

  @Bean
  public @NotNull Advice sentryCaptureExceptionAdvice() {
    return new SentryCaptureExceptionAdvice();
  }

  @Bean
  public @NotNull Advisor sentryCaptureExceptionAdvisor(
      final @NotNull @Qualifier("sentryCaptureExceptionPointcut") Pointcut sentryCaptureExceptionPointcut,
      final @NotNull @Qualifier("sentryCaptureExceptionAdvice") Advice sentryCaptureExceptionAdvice) {
    return new DefaultPointcutAdvisor(sentryCaptureExceptionPointcut, sentryCaptureExceptionAdvice);
  }
}
