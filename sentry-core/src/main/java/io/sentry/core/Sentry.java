package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import io.sentry.core.protocol.User;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sentry SDK main API entry point */
public final class Sentry {

  private Sentry() {}

  private static final @NotNull ThreadLocal<IHub> currentHub = new ThreadLocal<>();

  private static volatile @NotNull IHub mainHub = NoOpHub.getInstance();

  private static @NotNull IHub getCurrentHub() {
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

  public static @NotNull SentryId captureEvent(@NotNull SentryEvent event) {
    return getCurrentHub().captureEvent(event);
  }

  public static @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Object hint) {
    return getCurrentHub().captureEvent(event, hint);
  }

  public static @NotNull SentryId captureMessage(@NotNull String message) {
    return getCurrentHub().captureMessage(message);
  }

  public static @NotNull SentryId captureMessage(
      @NotNull String message, @NotNull SentryLevel level) {
    return getCurrentHub().captureMessage(message, level);
  }

  public static @NotNull SentryId captureException(@NotNull Throwable throwable) {
    return getCurrentHub().captureException(throwable);
  }

  public static @NotNull SentryId captureException(
      @NotNull Throwable throwable, @Nullable Object hint) {
    return getCurrentHub().captureException(throwable, hint);
  }

  public static void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Object hint) {
    getCurrentHub().addBreadcrumb(breadcrumb, hint);
  }

  public static void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {
    getCurrentHub().addBreadcrumb(breadcrumb);
  }

  public static void setLevel(@Nullable SentryLevel level) {
    getCurrentHub().setLevel(level);
  }

  public static void setTransaction(@Nullable String transaction) {
    getCurrentHub().setTransaction(transaction);
  }

  public static void setUser(@Nullable User user) {
    getCurrentHub().setUser(user);
  }

  public static void setFingerprint(@NotNull List<String> fingerprint) {
    getCurrentHub().setFingerprint(fingerprint);
  }

  public static void clearBreadcrumbs() {
    getCurrentHub().clearBreadcrumbs();
  }

  public static void setTag(@NotNull String key, @NotNull String value) {
    getCurrentHub().setTag(key, value);
  }

  public static void setExtra(@NotNull String key, @NotNull String value) {
    getCurrentHub().setExtra(key, value);
  }

  public static @NotNull SentryId getLastEventId() {
    return getCurrentHub().getLastEventId();
  }

  public static void pushScope() {
    getCurrentHub().pushScope();
  }

  public static void popScope() {
    getCurrentHub().popScope();
  }

  public static void withScope(@NotNull ScopeCallback callback) {
    getCurrentHub().withScope(callback);
  }

  public static void configureScope(@NotNull ScopeCallback callback) {
    getCurrentHub().configureScope(callback);
  }

  public static void bindClient(@NotNull ISentryClient client) {
    getCurrentHub().bindClient(client);
  }

  public static void flush(int timeoutMills) {
    getCurrentHub().flush(timeoutMills);
  }

  public interface OptionsConfiguration<T extends SentryOptions> {
    void configure(@NotNull T options);
  }
}
