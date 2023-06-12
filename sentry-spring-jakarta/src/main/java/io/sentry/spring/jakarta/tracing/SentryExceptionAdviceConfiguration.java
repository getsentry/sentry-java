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

/** Creates advice infrastructure for {@link SentryCaptureException}. */
@Configuration(proxyBeanMethods = false)
@Open
public class SentryExceptionAdviceConfiguration {

  @Bean
  public @NotNull Advice sentryCaptureExceptionAdvice(final @NotNull IHub hub) {
    return new SentryCaptureExceptionAdvice(hub);
  }

  @Bean
  public @NotNull Advisor sentryCaptureExceptionAdvisor(
      final @NotNull @Qualifier("sentryCaptureExceptionPointcut") Pointcut
              sentryCaptureExceptionPointcut,
      final @NotNull @Qualifier("sentryCaptureExceptionAdvice") Advice
              sentryCaptureExceptionAdvice) {
    return new DefaultPointcutAdvisor(sentryCaptureExceptionPointcut, sentryCaptureExceptionAdvice);
  }
}
