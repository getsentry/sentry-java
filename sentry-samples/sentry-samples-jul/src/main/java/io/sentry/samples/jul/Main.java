package io.sentry.samples.jul;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

  private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) {
    LOGGER.config("Hello Sentry!");

    // logging arguments are converted to Sentry Event parameters
    LOGGER.log(Level.INFO, "User has made a purchase of product: %d", 445);

    try {
      throw new RuntimeException("Invalid productId=445");
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Something went wrong", e);
    }
  }
}
