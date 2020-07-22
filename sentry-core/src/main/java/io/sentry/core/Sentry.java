package io.sentry.core;

import io.sentry.core.cache.DiskCache;
import io.sentry.core.cache.SessionCache;
import io.sentry.core.protocol.SentryId;
import io.sentry.core.protocol.User;
import java.io.File;
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
      hub = mainHub.clone();
      currentHub.set(hub);
    }
    return hub;
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
   * Initializes the SDK
   *
   * @param clazz OptionsContainer for SentryOptions
   * @param optionsConfiguration configuration options callback
   * @param <T> class that extends SentryOptions
   * @throws IllegalAccessException the IllegalAccessException
   * @throws InstantiationException the InstantiationException
   * @throws NoSuchMethodException the NoSuchMethodException
   * @throws InvocationTargetException the InvocationTargetException
   */
  public static <T extends SentryOptions> void init(
      final @NotNull OptionsContainer<T> clazz,
      final @NotNull OptionsConfiguration<T> optionsConfiguration)
      throws IllegalAccessException, InstantiationException, NoSuchMethodException,
          InvocationTargetException {
    init(clazz, optionsConfiguration, GLOBAL_HUB_DEFAULT_MODE);
  }

  /**
   * Initializes the SDK
   *
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
      final @NotNull OptionsContainer<T> clazz,
      final @NotNull OptionsConfiguration<T> optionsConfiguration,
      final boolean globalHubMode)
      throws IllegalAccessException, InstantiationException, NoSuchMethodException,
          InvocationTargetException {
    final T options = clazz.createInstance();
    optionsConfiguration.configure(options);
    init(options, globalHubMode);
  }

  /**
   * Initializes the SDK with an optional configuration options callback.
   *
   * @param optionsConfiguration configuration options callback
   */
  public static void init(final @NotNull OptionsConfiguration<SentryOptions> optionsConfiguration) {
    init(optionsConfiguration, GLOBAL_HUB_DEFAULT_MODE);
  }

  /**
   * Initializes the SDK with an optional configuration options callback.
   *
   * @param optionsConfiguration configuration options callback
   * @param globalHubMode the globalHubMode
   */
  public static void init(
      final @NotNull OptionsConfiguration<SentryOptions> optionsConfiguration,
      final boolean globalHubMode) {
    final SentryOptions options = new SentryOptions();
    optionsConfiguration.configure(options);
    init(options, globalHubMode);
  }

  /**
   * Initializes the SDK with a SentryOptions and globalHubMode
   *
   * @param options options the SentryOptions
   * @param globalHubMode the globalHubMode
   */
  private static synchronized void init(
      final @NotNull SentryOptions options, final boolean globalHubMode) {
    if (!initConfigurations(options)) {
      return;
    }

    options.getLogger().log(SentryLevel.INFO, "GlobalHubMode: '%s'", String.valueOf(globalHubMode));
    Sentry.globalHubMode = globalHubMode;

    final IHub hub = getCurrentHub();
    mainHub = new Hub(options);

    currentHub.set(mainHub);

    hub.close();

    // when integrations are registered on Hub ctor and async integrations are fired,
    // it might and actually happened that integrations called captureSomething
    // and hub was still NoOp.
    // Registering integrations here make sure that Hub is already created.
    for (final Integration integration : options.getIntegrations()) {
      integration.register(HubAdapter.getInstance(), options);
    }
  }

  private static boolean initConfigurations(final @NotNull SentryOptions options) {
    final String dsn = options.getDsn();
    if (dsn == null) {
      throw new IllegalArgumentException("DSN is required. Use empty string to disable SDK.");
    } else if (dsn.isEmpty()) {
      close();
      return false;
    }

    @SuppressWarnings("unused")
    final Dsn parsedDsn = new Dsn(dsn);

    ILogger logger = options.getLogger();

    if (options.isDebug() && logger instanceof NoOpLogger) {
      options.setLogger(new SystemOutLogger());
      logger = options.getLogger();
    }
    logger.log(SentryLevel.INFO, "Initializing SDK with DSN: '%s'", options.getDsn());

    // TODO: read values from conf file, Build conf or system envs
    // eg release, distinctId, sentryClientName

    if (options.getSerializer() instanceof NoOpSerializer) {
      options.setSerializer(new GsonSerializer(logger, options.getEnvelopeReader()));
    }

    // this should be after setting serializers
    if (options.getCacheDirPath() != null && !options.getCacheDirPath().isEmpty()) {
      final File cacheDir = new File(options.getCacheDirPath());
      cacheDir.mkdirs();

      final File outboxDir = new File(options.getOutboxPath());
      outboxDir.mkdirs();

      final File sessionsDir = new File(options.getSessionsPath());
      sessionsDir.mkdirs();

      options.setEventDiskCache(new DiskCache(options));
      options.setEnvelopeDiskCache(new SessionCache(options));
    } else {
      logger.log(SentryLevel.INFO, "No outbox dir path is defined in options.");
    }

    return true;
  }

  /** Close the SDK */
  public static synchronized void close() {
    final IHub hub = getCurrentHub();
    mainHub = NoOpHub.getInstance();
    hub.close();
  }

  /**
   * Captures the event.
   *
   * @param event the event
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureEvent(final @NotNull SentryEvent event) {
    return getCurrentHub().captureEvent(event);
  }

  /**
   * Captures the event.
   *
   * @param event the event
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureEvent(
      final @NotNull SentryEvent event, final @Nullable Object hint) {
    return getCurrentHub().captureEvent(event, hint);
  }

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureMessage(final @NotNull String message) {
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
      final @NotNull String message, final @NotNull SentryLevel level) {
    return getCurrentHub().captureMessage(message, level);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureException(final @NotNull Throwable throwable) {
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
      final @NotNull Throwable throwable, final @Nullable Object hint) {
    return getCurrentHub().captureException(throwable, hint);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   * @param hint SDK specific but provides high level information about the origin of the event
   */
  public static void addBreadcrumb(
      final @NotNull Breadcrumb breadcrumb, final @Nullable Object hint) {
    getCurrentHub().addBreadcrumb(breadcrumb, hint);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   */
  public static void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    getCurrentHub().addBreadcrumb(breadcrumb);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param message rendered as text and the whitespace is preserved.
   */
  public static void addBreadcrumb(final @NotNull String message) {
    getCurrentHub().addBreadcrumb(message);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param message rendered as text and the whitespace is preserved.
   * @param category Categories are dotted strings that indicate what the crumb is or where it comes
   *     from.
   */
  public static void addBreadcrumb(final @NotNull String message, final @NotNull String category) {
    getCurrentHub().addBreadcrumb(message, category);
  }

  /**
   * Sets the level of all events sent within current Scope
   *
   * @param level the Sentry level
   */
  public static void setLevel(final @Nullable SentryLevel level) {
    getCurrentHub().setLevel(level);
  }

  /**
   * Sets the name of the current transaction to the current Scope.
   *
   * @param transaction the transaction
   */
  public static void setTransaction(final @Nullable String transaction) {
    getCurrentHub().setTransaction(transaction);
  }

  /**
   * Shallow merges user configuration (email, username, etc) to the current Scope.
   *
   * @param user the user
   */
  public static void setUser(final @Nullable User user) {
    getCurrentHub().setUser(user);
  }

  /**
   * Sets the fingerprint to group specific events together to the current Scope.
   *
   * @param fingerprint the fingerprints
   */
  public static void setFingerprint(final @NotNull List<String> fingerprint) {
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
  public static void setTag(final @NotNull String key, final @NotNull String value) {
    getCurrentHub().setTag(key, value);
  }

  /**
   * Removes the tag to a string value to the current Scope
   *
   * @param key the key
   */
  public static void removeTag(final @NotNull String key) {
    getCurrentHub().removeTag(key);
  }

  /**
   * Sets the extra key to an arbitrary value to the current Scope, overwriting a potential previous
   * value
   *
   * @param key the key
   * @param value the value
   */
  public static void setExtra(final @NotNull String key, final @NotNull String value) {
    getCurrentHub().setExtra(key, value);
  }

  /**
   * Removes the extra key to an arbitrary value to the current Scope
   *
   * @param key the key
   */
  public static void removeExtra(final @NotNull String key) {
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
  public static void withScope(final @NotNull ScopeCallback callback) {
    getCurrentHub().withScope(callback);
  }

  /**
   * Configures the scope through the callback.
   *
   * @param callback The configure scope callback.
   */
  public static void configureScope(final @NotNull ScopeCallback callback) {
    getCurrentHub().configureScope(callback);
  }

  /**
   * Binds a different client to the current hub
   *
   * @param client the client.
   */
  public static void bindClient(final @NotNull ISentryClient client) {
    getCurrentHub().bindClient(client);
  }

  /**
   * Flushes events queued up to the current hub. Not implemented yet.
   *
   * @param timeoutMillis time in milliseconds
   */
  public static void flush(final long timeoutMillis) {
    getCurrentHub().flush(timeoutMillis);
  }

  /** Starts a new session. If there's a running session, it ends it before starting the new one. */
  public static void startSession() {
    getCurrentHub().startSession();
  }

  /** Ends the current session */
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
