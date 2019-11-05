package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import org.jetbrains.annotations.NotNull;

/** Sentry SDK main API entry point */
public final class Sentry {

  private Sentry() {}

  private static final ThreadLocal<IHub> currentHub = new ThreadLocal<>();

  private static volatile IHub mainHub = NoOpHub.getInstance();

  private static IHub getCurrentHub() {
    IHub hub = currentHub.get();
    if (hub == null) {
      currentHub.set(mainHub.clone());
    }
    return currentHub.get();
  }

  public static boolean isEnabled() {
    return getCurrentHub().isEnabled();
  }

  public static void init() {
    init(new SentryOptions());
  }

  public static void init(@NotNull OptionsConfiguration optionsConfiguration) {
    SentryOptions options = new SentryOptions();
    optionsConfiguration.configure(options);
    init(options);
  }

  private static synchronized void init(@NotNull SentryOptions options) {
    String dsn = options.getDsn();
    if (dsn == null || dsn.isEmpty()) {
      close();
      return;
    }

    @SuppressWarnings("unused")
    Dsn parsedDsn = new Dsn(dsn);

    ILogger logger = options.getLogger();
    logger.log(SentryLevel.INFO, "Initializing SDK with DSN: '%s'", options.getDsn());

    IHub hub = getCurrentHub();
    mainHub = new Hub(options);
    currentHub.set(mainHub);
    hub.close();
  }

  public static synchronized void close() {
    IHub hub = getCurrentHub();
    mainHub = NoOpHub.getInstance();
    hub.close();
  }

  public static SentryId captureEvent(SentryEvent event) {
    return getCurrentHub().captureEvent(event);
  }

  public static SentryId captureMessage(String message) {
    return getCurrentHub().captureMessage(message);
  }

  public static SentryId captureException(Throwable throwable) {
    return getCurrentHub().captureException(throwable);
  }

  public static void addBreadcrumb(Breadcrumb breadcrumb) {
    getCurrentHub().addBreadcrumb(breadcrumb);
  }

  public static SentryId getLastEventId() {
    return getCurrentHub().getLastEventId();
  }

  public static void pushScope() {
    getCurrentHub().pushScope();
  }

  public static void popScope() {
    getCurrentHub().popScope();
  }

  public static void withScope(ScopeCallback callback) {
    getCurrentHub().withScope(callback);
  }

  public static void configureScope(ScopeCallback callback) {
    getCurrentHub().configureScope(callback);
  }

  public static void bindClient(SentryClient client) {
    getCurrentHub().bindClient(client);
  }

  public static void flush(int timeoutMills) {
    getCurrentHub().flush(timeoutMills);
  }

  public interface OptionsConfiguration {
    void configure(SentryOptions options);
  }
}
