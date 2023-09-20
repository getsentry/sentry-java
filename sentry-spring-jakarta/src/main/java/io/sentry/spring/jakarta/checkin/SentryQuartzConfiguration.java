package io.sentry.spring.jakarta.checkin;

import com.jakewharton.nopen.annotation.Open;
import org.jetbrains.annotations.ApiStatus;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Open
@ApiStatus.Experimental
public class SentryQuartzConfiguration {

  @Bean
  public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer() {
    return new SentrySchedulerFactoryBeanCustomizer();
  }
}
