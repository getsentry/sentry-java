package io.sentry.samples.spring.boot;

import static io.sentry.quartz.SentryJobListener.SENTRY_SLUG_KEY;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.sentry.samples.spring.boot.quartz.SampleJob;
import java.util.Collections;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;
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

  @Bean
  public JobDetailFactoryBean jobDetail() {
    JobDetailFactoryBean jobDetailFactory = new JobDetailFactoryBean();
    jobDetailFactory.setJobClass(SampleJob.class);
    jobDetailFactory.setDurability(true);
    jobDetailFactory.setJobDataAsMap(
        Collections.singletonMap(SENTRY_SLUG_KEY, "monitor_slug_job_detail"));
    return jobDetailFactory;
  }

  @Bean
  public SimpleTriggerFactoryBean trigger(JobDetail job) {
    SimpleTriggerFactoryBean trigger = new SimpleTriggerFactoryBean();
    trigger.setJobDetail(job);
    trigger.setRepeatInterval(2 * 60 * 1000); // every two minutes
    trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
    trigger.setJobDataAsMap(
        Collections.singletonMap(SENTRY_SLUG_KEY, "monitor_slug_simple_trigger"));
    return trigger;
  }

  @Bean
  public CronTriggerFactoryBean cronTrigger(JobDetail job) {
    CronTriggerFactoryBean trigger = new CronTriggerFactoryBean();
    trigger.setJobDetail(job);
    trigger.setCronExpression("0 0/5 * ? * *"); // every five minutes
    return trigger;
  }

  @Bean
  public Tracer tracer() {
    return GlobalOpenTelemetry.get().getTracer("tracerForSpringBootDemo");
  }
}
