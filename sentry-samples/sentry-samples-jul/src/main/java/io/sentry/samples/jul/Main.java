package io.sentry.samples.jul;

import io.sentry.Sentry;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.slf4j.MDC;

public class Main {

  private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) throws Exception {
    // instead of the following line you can also pass
    // -Djava.util.logging.config.file=.../logging.properties to the
    // java command
    LogManager.getLogManager()
        .readConfiguration(Main.class.getClassLoader().getResourceAsStream("logging.properties"));
    LOGGER.config("Hello Sentry!");

    // MDC parameters are converted to Sentry Event tags
    MDC.put("userId", UUID.randomUUID().toString());
    MDC.put("requestId", UUID.randomUUID().toString());

    Sentry.addFeatureFlag("my-feature-flag", true);

    LOGGER.warning("important warning");

    // logging arguments are converted to Sentry Event parameters
    LOGGER.log(Level.INFO, "User has made a purchase of product: %d", 445);

    try {
      throw new RuntimeException("Invalid productId=445");
    } catch (Throwable e) {
      LOGGER.log(Level.SEVERE, "Something went wrong", e);
    }
  }
}
