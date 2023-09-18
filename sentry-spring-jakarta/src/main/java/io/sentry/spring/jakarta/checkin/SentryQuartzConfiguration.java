package io.sentry.spring.jakarta.checkin;

import com.jakewharton.nopen.annotation.Open;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Open
public class SentryQuartzConfiguration {

  @Bean
  public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer() {
    return new SentrySchedulerFactoryBeanCustomizer();
  }
}
