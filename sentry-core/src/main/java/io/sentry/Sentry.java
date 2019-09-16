package io.sentry;

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
    currentClient.close();
    currentClient = new SentryClient(options);
  }

  public static synchronized void close() {
    currentClient.close();
    currentClient = NoOpSentryClient.getInstance();
  }

  public interface OptionsConfiguration {
    void configure(SentryOptions options);
  }
}
