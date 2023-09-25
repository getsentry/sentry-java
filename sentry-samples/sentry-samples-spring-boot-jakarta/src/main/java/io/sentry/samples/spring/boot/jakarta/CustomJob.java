package io.sentry.samples.spring.boot.jakarta;

import io.sentry.CheckIn;
import io.sentry.CheckInStatus;
import io.sentry.DateUtils;
import io.sentry.Sentry;
import io.sentry.protocol.SentryId;
import io.sentry.spring.jakarta.tracing.SentryTransaction;
import org.jetbrains.annotations.NotNull;
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
  void execute() throws InterruptedException {
    final @NotNull SentryId checkInId =
        Sentry.captureCheckIn(new CheckIn("my_monitor_slug", CheckInStatus.IN_PROGRESS));
    final long startTime = System.currentTimeMillis();
    boolean didError = false;
    try {
      LOGGER.info("Executing scheduled job");
      Thread.sleep(2000L);
    } catch (Throwable t) {
      didError = true;
      throw t;
    } finally {
      final @NotNull CheckInStatus status = didError ? CheckInStatus.ERROR : CheckInStatus.OK;
      CheckIn checkIn = new CheckIn(checkInId, "my_monitor_slug", status);
      checkIn.setDuration(DateUtils.millisToSeconds(System.currentTimeMillis() - startTime));
      Sentry.captureCheckIn(checkIn);
    }
  }
}
