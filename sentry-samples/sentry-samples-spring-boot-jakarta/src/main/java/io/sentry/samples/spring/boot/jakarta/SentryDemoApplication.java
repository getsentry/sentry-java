package io.sentry.samples.spring.boot.jakarta;

import io.sentry.samples.spring.boot.jakarta.quartz.SampleJob;
import org.quartz.JobDetail;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableScheduling
public class SentryDemoApplication {
  public static void main(String[] args) {
    SpringApplication.run(SentryDemoApplication.class, args);
  }

  @Bean
  RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }

  @Bean
  WebClient webClient(WebClient.Builder builder) {
    return builder.build();
  }

  //  @Bean
  //  public JobDetail jobDetail() {
  //    return JobBuilder.newJob().ofType(SampleJob.class)
  //      .storeDurably()
  //      .withIdentity("Qrtz_Job_Detail")
  //      .withDescription("Invoke Sample Job service...")
  //      .build();
  //  }
  @Bean
  public JobDetailFactoryBean jobDetail() {
    JobDetailFactoryBean jobDetailFactory = new JobDetailFactoryBean();
    jobDetailFactory.setName("hello there 123");
    jobDetailFactory.setJobClass(SampleJob.class);
    jobDetailFactory.setDescription("Invoke Sample Job service...");
    jobDetailFactory.setDurability(true);
    return jobDetailFactory;
  }

  //  @Bean
  //  public SimpleTriggerFactoryBean trigger(JobDetail job) {
  //    SimpleTriggerFactoryBean trigger = new SimpleTriggerFactoryBean();
  //    trigger.setJobDetail(job);
  //    trigger.setRepeatInterval(2 * 60 * 1000);
  //    trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
  //    return trigger;
  //  }

  @Bean
  public CronTriggerFactoryBean trigger(JobDetail job) {
    CronTriggerFactoryBean trigger = new CronTriggerFactoryBean();
    trigger.setJobDetail(job);
    trigger.setCronExpression("0 /5 * ? * *");
    return trigger;
  }

  //  @Bean
  //  public Scheduler scheduler(Trigger trigger, JobDetail job, SchedulerFactoryBean factory)
  //    throws SchedulerException {
  //    Scheduler scheduler = factory.getScheduler();
  //    scheduler.scheduleJob(job, trigger);
  //    scheduler.start();
  //    return scheduler;
  //  }

  //  @Bean
  //  public SchedulerFactoryBean scheduler(Trigger trigger, JobDetail job, DataSource
  // quartzDataSource) {
  //    SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
  //    schedulerFactory.setConfigLocation(new ClassPathResource("quartz.properties"));
  ////    schedulerFactory.setGlobalJobListeners(new SentryJobListener());
  ////    schedulerFactory.setGlobalTriggerListeners(new SentryTriggerListener());
  ////    try {
  ////      ListenerManager listenerManager = schedulerFactory.getScheduler().getListenerManager();
  ////      listenerManager.addJobListener(new SentryJobListener());
  ////      listenerManager.addTriggerListener(new SentryTriggerListener());
  ////    } catch (SchedulerException e) {
  ////      throw new RuntimeException(e);
  ////    }
  //
  ////    schedulerFactory.setJobFactory(springBeanJobFactory());
  //    schedulerFactory.setJobDetails(job);
  //    schedulerFactory.setTriggers(trigger);
  //    schedulerFactory.setDataSource(quartzDataSource);
  //    return schedulerFactory;
  //  }
}
