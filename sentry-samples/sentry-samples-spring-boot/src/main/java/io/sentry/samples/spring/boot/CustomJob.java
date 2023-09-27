package io.sentry.samples.spring.boot;

import io.sentry.spring.checkin.SentryCheckIn;
import io.sentry.spring.tracing.SentryTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@link SentryTransaction} added on the class level, creates transaction around each method
 * execution of every method of the annotated class.
 */
@Component
@SentryTransaction(operation = "scheduled")
public class CustomJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomJob.class);

  @Scheduled(fixedRate = 3 * 60 * 1000L)
  @SentryCheckIn("monitor_slug_2")
  void execute() throws InterruptedException {
    LOGGER.info("Executing scheduled job");
    Thread.sleep(2000L);
  }
}
