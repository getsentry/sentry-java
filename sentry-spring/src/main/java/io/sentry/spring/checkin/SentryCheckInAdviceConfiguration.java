package io.sentry.spring.checkin;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import org.aopalliance.aop.Advice;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Open
@ApiStatus.Experimental
public class SentryCheckInAdviceConfiguration {

  @Bean
  public @NotNull Advice sentryCheckInAdvice(final @NotNull IHub hub) {
    return new SentryCheckInAdvice(hub);
  }

  @Bean
  public @NotNull Advisor sentryCheckInAdvisor(
      final @NotNull @Qualifier("sentryCheckInPointcut") Pointcut sentryCheckInPointcut,
      final @NotNull @Qualifier("sentryCheckInAdvice") Advice sentryCheckInAdvice) {
    return new DefaultPointcutAdvisor(sentryCheckInPointcut, sentryCheckInAdvice);
  }
}
