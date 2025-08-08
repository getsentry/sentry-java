package io.sentry.spring.jakarta.checkin;

import com.jakewharton.nopen.annotation.Open;
import org.jetbrains.annotations.ApiStatus;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
@Open
@ApiStatus.Experimental
public class SentryQuartzConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer() {
    return new SentrySchedulerFactoryBeanCustomizer();
  }
}
