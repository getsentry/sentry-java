package io.sentry.spring7.checkin;

import com.jakewharton.nopen.annotation.Open;
import org.aopalliance.aop.Advice;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

@Configuration(proxyBeanMethods = false)
@Open
@ApiStatus.Experimental
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class SentryCheckInAdviceConfiguration {

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advice sentryCheckInAdvice() {
    return new SentryCheckInAdvice();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advisor sentryCheckInAdvisor(
      final @NotNull @Qualifier("sentryCheckInPointcut") Pointcut sentryCheckInPointcut,
      final @NotNull @Qualifier("sentryCheckInAdvice") Advice sentryCheckInAdvice) {
    return new DefaultPointcutAdvisor(sentryCheckInPointcut, sentryCheckInAdvice);
  }
}
