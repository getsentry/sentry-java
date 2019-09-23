package io.sentry;

import io.sentry.protocol.SentryId;

public final class Sentry {

  private Sentry() {}

  private static ISentryClient currentClient = NoOpSentryClient.getInstance();

  public static boolean isEnabled() {
    return currentClient.isEnabled();
  }

  public static void init() {
    init(new SentryOptions());
  }

  public static void init(OptionsConfiguration optionsConfiguration) {
    SentryOptions options = new SentryOptions();
    optionsConfiguration.configure(options);
    init(options);
  }

  static synchronized void init(SentryOptions options) {
    ILogger logger = options.getLogger();
    if (logger != null) {
      logger.log(SentryLevel.Info, "Initializing SDK with DSN: '%d'", options.getDsn());
    }
    currentClient.close();
    currentClient = new SentryClient(options);
  }

  public static synchronized void close() {
    currentClient.close();
    currentClient = NoOpSentryClient.getInstance();
  }

  public static SentryId captureEvent(SentryEvent event) {
    return currentClient.captureEvent(event);
  }

  public static SentryId captureMessage(String message) {
    return currentClient.captureMessage(message);
  }

  public static SentryId captureException(Throwable throwable) {
    return currentClient.captureException(throwable);
  }

  public interface OptionsConfiguration {
    void configure(SentryOptions options);
  }
}
