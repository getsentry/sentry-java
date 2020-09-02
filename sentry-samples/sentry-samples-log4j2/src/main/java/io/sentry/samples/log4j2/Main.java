package io.sentry.samples.log4j2;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

public class Main {
  private static final Logger LOGGER = LogManager.getLogger(Main.class);

  public static void main(String[] args) {
    // The SDK was initialized through the appender configuration because a DSN was set there.
    // Update the DSN in log4j2.xml to see these events in your Sentry dashboard.
    LOGGER.debug("Hello Sentry!");

    // ThreadContext parameters are converted to Sentry Event tags
    ThreadContext.put("userId", UUID.randomUUID().toString());

    // logging arguments are converted to Sentry Event parameters
    LOGGER.info("User has made a purchase of product: {}", 445);
    // because minimumEventLevel is set to WARN this raises an event
    LOGGER.warn("Important warning");

    try {
      throw new RuntimeException("Invalid productId=445");
    } catch (Exception e) {
      LOGGER.error("Something went wrong", e);
    }
  }
}
