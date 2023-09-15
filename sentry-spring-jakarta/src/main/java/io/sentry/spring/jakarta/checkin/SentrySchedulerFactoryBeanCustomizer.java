package io.sentry.spring.jakarta.checkin;

import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

public final class SentrySchedulerFactoryBeanCustomizer implements SchedulerFactoryBeanCustomizer {
  @Override
  public void customize(SchedulerFactoryBean schedulerFactoryBean) {
    schedulerFactoryBean.setGlobalJobListeners(new SentryJobListener());
  }
}
