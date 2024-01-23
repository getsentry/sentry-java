package io.sentry.spring.jakarta.exception;

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

/** Creates advice infrastructure for {@link SentryCaptureExceptionParameter}. */
@Configuration(proxyBeanMethods = false)
@Open
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class SentryExceptionParameterAdviceConfiguration {

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advice sentryCaptureExceptionParameterAdvice() {
    return new SentryCaptureExceptionParameterAdvice();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advisor sentryCaptureExceptionParameterAdvisor(
      final @NotNull @Qualifier("sentryCaptureExceptionParameterPointcut") Pointcut
              sentryCaptureExceptionParameterPointcut,
      final @NotNull @Qualifier("sentryCaptureExceptionParameterAdvice") Advice
              sentryCaptureExceptionParameterAdvice) {
    return new DefaultPointcutAdvisor(
        sentryCaptureExceptionParameterPointcut, sentryCaptureExceptionParameterAdvice);
  }
}
