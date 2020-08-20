package io.sentry.samples.logback;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class Main {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    LOGGER.debug("Hello Sentry!");

    // MDC parameters are converted to Sentry Event tags
    MDC.put("userId", UUID.randomUUID().toString());

    // logging arguments are converted to Sentry Event parameters
    LOGGER.info("User has made a purchase of product: {}", 445);

    try {
      throw new RuntimeException("Invalid productId=445");
    } catch (Exception e) {
      LOGGER.error("Something went wrong", e);
    }
  }
}
