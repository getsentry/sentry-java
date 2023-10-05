package io.sentry.spring.checkin;

import io.sentry.quartz.SentryJobListener;
import org.jetbrains.annotations.ApiStatus;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

@ApiStatus.Experimental
public final class SentrySchedulerFactoryBeanCustomizer implements SchedulerFactoryBeanCustomizer {
  @Override
  public void customize(SchedulerFactoryBean schedulerFactoryBean) {
    schedulerFactoryBean.setGlobalJobListeners(new SentryJobListener());
  }
}
