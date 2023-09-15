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

  //  @Bean
  //  public TaskSchedulerCustomizer taskSchedulerCustomizer() {
  //    return new SentryTaskSchedulerCustomizer();
  //  }

  //  public final class SentryTaskSchedulerCustomizer implements TaskSchedulerCustomizer {
  //    @Override
  //    public void customize(ThreadPoolTaskScheduler taskScheduler) {
  //      System.out.println("customize taskScheduler");
  ////    taskScheduler.getScheduledExecutor().
  //    }
  //  }

  //  @Autowired
  //  private SchedulerFactoryBean schedulerFactoryBean;

  //  @PostConstruct
  //  public void addListeners() throws SchedulerException {
  //    ListenerManager listenerManager = schedulerFactoryBean.getScheduler()
  //      .getListenerManager();
  //
  //    listenerManager.addTriggerListener(new SentryTriggerListener());
  //    listenerManager.addJobListener(new SentryJobListener());
  //  }
}
