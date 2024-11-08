package io.sentry.samples.spring.boot.jakarta.quartz;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
public class SampleJob implements Job {

  public void execute(JobExecutionContext context) throws JobExecutionException {
    System.out.println("running job");
    try {
      Thread.sleep(15000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
