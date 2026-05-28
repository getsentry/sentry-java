package io.sentry.samples.spring.boot4.otlp;

import io.sentry.spring7.checkin.SentryCheckIn;
import io.sentry.spring7.tracing.SentryTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link SentryTransaction} added on the class level, creates transaction around each method
 * execution of every method of the annotated class.
 */
@Component
@SentryTransaction(operation = "scheduled")
public class CustomJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomJob.class);

  @SentryCheckIn("monitor_slug_1")
  //  @Scheduled(fixedRate = 3 * 60 * 1000L)
  void execute() throws InterruptedException {
    LOGGER.info("Executing scheduled job");
    Thread.sleep(2000L);
  }
}
