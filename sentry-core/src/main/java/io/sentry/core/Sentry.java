package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  // Used by integrations that define their own SentryOptions
  public static <T extends SentryOptions> void init(
      @NotNull OptionsContainer<T> clazz, @NotNull OptionsConfiguration<T> optionsConfiguration)
      throws IllegalAccessException, InstantiationException, NoSuchMethodException,
          InvocationTargetException {
    T options = clazz.createInstance();
    optionsConfiguration.configure(options);
    init(options);
  }

  public static void init(@NotNull OptionsConfiguration<SentryOptions> optionsConfiguration) {
    SentryOptions options = new SentryOptions();
    optionsConfiguration.configure(options);
    init(options);
  }

  private static synchronized <T extends SentryOptions> void init(@NotNull T options) {
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

  public static SentryId captureEvent(SentryEvent event, @Nullable Object hint) {
    return getCurrentHub().captureEvent(event, hint);
  }

  public static SentryId captureMessage(String message) {
    return getCurrentHub().captureMessage(message);
  }

  public static SentryId captureException(Throwable throwable) {
    return getCurrentHub().captureException(throwable);
  }

  public static SentryId captureException(Throwable throwable, @Nullable Object hint) {
    return getCurrentHub().captureException(throwable, hint);
  }

  public static void addBreadcrumb(Breadcrumb breadcrumb, @Nullable Object hint) {
    getCurrentHub().addBreadcrumb(breadcrumb, hint);
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

  public interface OptionsConfiguration<T extends SentryOptions> {
    void configure(T options);
  }
}
