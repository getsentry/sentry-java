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

  /** Holds Hubs per thread or only mainHub if globalHubMode is enabled. */
  private static final @NotNull ThreadLocal<IHub> currentHub = new ThreadLocal<>();

  /** The Main Hub or NoOp if Sentry is disabled. */
  private static volatile @NotNull IHub mainHub = NoOpHub.getInstance();

  /** Default value for globalHubMode is false */
  private static final boolean GLOBAL_HUB_DEFAULT_MODE = false;

  /** whether to use a single (global) Hub as opposed to one per thread. */
  private static volatile boolean globalHubMode = GLOBAL_HUB_DEFAULT_MODE;

  /**
   * Returns the current (threads) hub, if none, clones the mainHub and returns it.
   *
   * @return the hub
   */
  static @NotNull IHub getCurrentHub() {
    if (globalHubMode) {
      return mainHub;
    }
    IHub hub = currentHub.get();
    if (hub == null) {
      currentHub.set(mainHub.clone());
    }
    return currentHub.get();
  }

  /**
   * Check if the current Hub is enabled/active.
   *
   * @return true if its enabled or false otherwise.
   */
  public static boolean isEnabled() {
    return getCurrentHub().isEnabled();
  }

  /** Initializes the SDK */
  public static void init() {
    init(new SentryOptions(), GLOBAL_HUB_DEFAULT_MODE);
  }

  /**
   * @param clazz OptionsContainer for SentryOptions
   * @param optionsConfiguration configuration options callback
   * @param <T> class that extends SentryOptions
   * @throws IllegalAccessException the IllegalAccessException
   * @throws InstantiationException the InstantiationException
   * @throws NoSuchMethodException the NoSuchMethodException
   * @throws InvocationTargetException the InvocationTargetException
   */
  public static <T extends SentryOptions> void init(
      @NotNull OptionsContainer<T> clazz, @NotNull OptionsConfiguration<T> optionsConfiguration)
      throws IllegalAccessException, InstantiationException, NoSuchMethodException,
          InvocationTargetException {
    init(clazz, optionsConfiguration, GLOBAL_HUB_DEFAULT_MODE);
  }

  /**
   * @param clazz OptionsContainer for SentryOptions
   * @param optionsConfiguration configuration options callback
   * @param globalHubMode the globalHubMode
   * @param <T> class that extends SentryOptions
   * @throws IllegalAccessException the IllegalAccessException
   * @throws InstantiationException the InstantiationException
   * @throws NoSuchMethodException the NoSuchMethodException
   * @throws InvocationTargetException the InvocationTargetException
   */
  public static <T extends SentryOptions> void init(
      @NotNull OptionsContainer<T> clazz,
      @NotNull OptionsConfiguration<T> optionsConfiguration,
      boolean globalHubMode)
      throws IllegalAccessException, InstantiationException, NoSuchMethodException,
          InvocationTargetException {
    T options = clazz.createInstance();
    optionsConfiguration.configure(options);
    init(options, globalHubMode);
  }

  /**
   * Initializes the SDK with an optional configuration options callback.
   *
   * @param optionsConfiguration configuration options callback
   */
  public static void init(@NotNull OptionsConfiguration<SentryOptions> optionsConfiguration) {
    init(optionsConfiguration, GLOBAL_HUB_DEFAULT_MODE);
  }

  /**
   * Initializes the SDK with an optional configuration options callback.
   *
   * @param optionsConfiguration configuration options callback
   * @param globalHubMode the globalHubMode
   */
  public static void init(
      @NotNull OptionsConfiguration<SentryOptions> optionsConfiguration, boolean globalHubMode) {
    SentryOptions options = new SentryOptions();
    optionsConfiguration.configure(options);
    init(options, globalHubMode);
  }

  /**
   * Initializes the SDK with a SentryOptions and globalHubMode
   *
   * @param options options the SentryOptions
   * @param globalHubMode the globalHubMode
   * @param <T> a class that extends SentryOptions or SentryOptions itself.
   */
  private static synchronized <T extends SentryOptions> void init(
      @NotNull T options, boolean globalHubMode) {
    String dsn = options.getDsn();
    if (dsn == null) {
      throw new IllegalArgumentException("DSN is required. Use empty string to disable SDK.");
    } else if (dsn.isEmpty()) {
      close();
      return;
    }

    @SuppressWarnings("unused")
    Dsn parsedDsn = new Dsn(dsn);

    ILogger logger = options.getLogger();
    logger.log(SentryLevel.INFO, "Initializing SDK with DSN: '%s'", options.getDsn());

    logger.log(SentryLevel.INFO, "GlobalHubMode: '%s'", String.valueOf(globalHubMode));
    Sentry.globalHubMode = globalHubMode;

    IHub hub = getCurrentHub();
    mainHub = new Hub(options);
    currentHub.set(mainHub);
    hub.close();
  }

  /** Close the SDK */
  public static synchronized void close() {
    IHub hub = getCurrentHub();
    mainHub = NoOpHub.getInstance();
    hub.close();
  }

  /**
   * Captures the event.
   *
   * @param event the event
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureEvent(@NotNull SentryEvent event) {
    return getCurrentHub().captureEvent(event);
  }

  /**
   * Captures the event.
   *
   * @param event the event
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Object hint) {
    return getCurrentHub().captureEvent(event, hint);
  }

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureMessage(@NotNull String message) {
    return getCurrentHub().captureMessage(message);
  }

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @param level The message level.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureMessage(
      @NotNull String message, @NotNull SentryLevel level) {
    return getCurrentHub().captureMessage(message, level);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureException(@NotNull Throwable throwable) {
    return getCurrentHub().captureException(throwable);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureException(
      @NotNull Throwable throwable, @Nullable Object hint) {
    return getCurrentHub().captureException(throwable, hint);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   * @param hint SDK specific but provides high level information about the origin of the event
   */
  public static void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Object hint) {
    getCurrentHub().addBreadcrumb(breadcrumb, hint);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   */
  public static void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {
    getCurrentHub().addBreadcrumb(breadcrumb);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param message rendered as text and the whitespace is preserved.
   */
  public static void addBreadcrumb(@NotNull String message) {
    getCurrentHub().addBreadcrumb(message);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param message rendered as text and the whitespace is preserved.
   * @param category Categories are dotted strings that indicate what the crumb is or where it comes
   *     from.
   */
  public static void addBreadcrumb(@NotNull String message, @NotNull String category) {
    getCurrentHub().addBreadcrumb(message, category);
  }

  /**
   * Sets the level of all events sent within current Scope
   *
   * @param level the Sentry level
   */
  public static void setLevel(@Nullable SentryLevel level) {
    getCurrentHub().setLevel(level);
  }

  /**
   * Sets the name of the current transaction to the current Scope.
   *
   * @param transaction the transaction
   */
  public static void setTransaction(@Nullable String transaction) {
    getCurrentHub().setTransaction(transaction);
  }

  /**
   * Shallow merges user configuration (email, username, etc) to the current Scope.
   *
   * @param user the user
   */
  public static void setUser(@Nullable User user) {
    getCurrentHub().setUser(user);
  }

  /**
   * Sets the fingerprint to group specific events together to the current Scope.
   *
   * @param fingerprint the fingerprints
   */
  public static void setFingerprint(@NotNull List<String> fingerprint) {
    getCurrentHub().setFingerprint(fingerprint);
  }

  /** Deletes current breadcrumbs from the current scope. */
  public static void clearBreadcrumbs() {
    getCurrentHub().clearBreadcrumbs();
  }

  /**
   * Sets the tag to a string value to the current Scope, overwriting a potential previous value
   *
   * @param key the key
   * @param value the value
   */
  public static void setTag(@NotNull String key, @NotNull String value) {
    getCurrentHub().setTag(key, value);
  }

  /**
   * Removes the tag to a string value to the current Scope
   *
   * @param key the key
   */
  public static void removeTag(@NotNull String key) {
    getCurrentHub().removeTag(key);
  }

  /**
   * Sets the extra key to an arbitrary value to the current Scope, overwriting a potential previous
   * value
   *
   * @param key the key
   * @param value the value
   */
  public static void setExtra(@NotNull String key, @NotNull String value) {
    getCurrentHub().setExtra(key, value);
  }

  /**
   * Removes the extra key to an arbitrary value to the current Scope
   *
   * @param key the key
   */
  public static void removeExtra(@NotNull String key) {
    getCurrentHub().removeExtra(key);
  }

  /**
   * Last event id recorded in the current scope
   *
   * @return last SentryId
   */
  public static @NotNull SentryId getLastEventId() {
    return getCurrentHub().getLastEventId();
  }

  /** Pushes a new scope while inheriting the current scope's data. */
  public static void pushScope() {
    // pushScope is no-op in global hub mode
    if (!globalHubMode) {
      getCurrentHub().pushScope();
    }
  }

  /** Removes the first scope */
  public static void popScope() {
    // popScope is no-op in global hub mode
    if (!globalHubMode) {
      getCurrentHub().popScope();
    }
  }

  /**
   * Runs the callback with a new scope which gets dropped at the end
   *
   * @param callback the callback
   */
  public static void withScope(@NotNull ScopeCallback callback) {
    getCurrentHub().withScope(callback);
  }

  /**
   * Configures the scope through the callback.
   *
   * @param callback The configure scope callback.
   */
  public static void configureScope(@NotNull ScopeCallback callback) {
    getCurrentHub().configureScope(callback);
  }

  /**
   * Binds a different client to the current hub
   *
   * @param client the client.
   */
  public static void bindClient(@NotNull ISentryClient client) {
    getCurrentHub().bindClient(client);
  }

  /**
   * Flushes events queued up to the current hub. Not implemented yet.
   *
   * @param timeoutMills time in milliseconds
   */
  public static void flush(long timeoutMills) {
    getCurrentHub().flush(timeoutMills);
  }

  public static void startSession() {
    getCurrentHub().startSession();
  }

  public static void endSession() {
    getCurrentHub().endSession();
  }

  /**
   * Configuration options callback
   *
   * @param <T> a class that extends SentryOptions or SentryOptions itself.
   */
  public interface OptionsConfiguration<T extends SentryOptions> {

    /**
     * configure the options
     *
     * @param options the options
     */
    void configure(@NotNull T options);
  }
}
