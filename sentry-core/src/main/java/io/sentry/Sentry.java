package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.NonNull;

public final class Sentry {

  private Sentry() {}

  private static IHub currentHub = NoOpHub.getInstance();

  public static boolean isEnabled() {
    return currentHub.isEnabled();
  }

  public static void init() {
    init(new SentryOptions());
  }

  public static void init(@NonNull OptionsConfiguration optionsConfiguration) {
    SentryOptions options = new SentryOptions();
    if (optionsConfiguration != null) {
      optionsConfiguration.configure(options);
    }
    init(options);
  }

  static synchronized void init(@NonNull SentryOptions options) {
    String dsn = options.getDsn();
    if (dsn == null || dsn.isEmpty()) {
      return;
    }

    Dsn parsedDsn = new Dsn(dsn);

    ILogger logger = options.getLogger();
    if (logger != null) {
      logger.log(SentryLevel.Info, "Initializing SDK with DSN: '%d'", options.getDsn());
    }
    currentHub.close();
    currentHub = new Hub(options);
  }

  public static synchronized void close() {
    currentHub.close();
    currentHub = NoOpHub.getInstance();
  }

  public static SentryId captureEvent(SentryEvent event) {
    return currentHub.captureEvent(event);
  }

  public static SentryId captureMessage(String message) {
    return currentHub.captureMessage(message);
  }

  public static SentryId captureException(Throwable throwable) {
    return currentHub.captureException(throwable);
  }

  public interface OptionsConfiguration {
    void configure(SentryOptions options);
  }
}
