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

/** Creates advice infrastructure for {@link SentryCaptureExceptionParameter}. */
@Configuration(proxyBeanMethods = false)
@Open
public class SentryExceptionParameterAdviceConfiguration {

  @Bean
  public @NotNull Advice sentryCaptureExceptionParameterAdvice(final @NotNull IHub hub) {
    return new SentryCaptureExceptionParameterAdvice(hub);
  }

  @Bean
  public @NotNull Advisor sentryCaptureExceptionParameterAdvisor(
      final @NotNull @Qualifier("sentryCaptureExceptionParameterPointcut") Pointcut
              sentryCaptureExceptionParameterPointcut,
      final @NotNull @Qualifier("sentryCaptureExceptionParameterAdvice") Advice
              sentryCaptureExceptionParameterAdvice) {
    return new DefaultPointcutAdvisor(
        sentryCaptureExceptionParameterPointcut, sentryCaptureExceptionParameterAdvice);
  }
}
