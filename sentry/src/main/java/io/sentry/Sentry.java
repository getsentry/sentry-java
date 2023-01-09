package io.sentry;

import io.sentry.cache.EnvelopeCache;
import io.sentry.cache.IEnvelopeCache;
import io.sentry.config.PropertiesProviderFactory;
import io.sentry.internal.modules.IModulesLoader;
import io.sentry.internal.modules.NoOpModulesLoader;
import io.sentry.internal.modules.ResourcesModulesLoader;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.transport.NoOpEnvelopeCache;
import io.sentry.util.FileUtils;
import io.sentry.util.thread.IMainThreadChecker;
import io.sentry.util.thread.MainThreadChecker;
import io.sentry.util.thread.NoOpMainThreadChecker;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
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
  @ApiStatus.Internal // exposed for the coroutines integration in SentryContext
  public static @NotNull IHub getCurrentHub() {
    if (globalHubMode) {
      return mainHub;
    }
    IHub hub = currentHub.get();
    if (hub == null || hub instanceof NoOpHub) {
      hub = mainHub.clone();
      currentHub.set(hub);
    }
    return hub;
  }

  @ApiStatus.Internal // exposed for the coroutines integration in SentryContext
  public static void setCurrentHub(final @NotNull IHub hub) {
    currentHub.set(hub);
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
    init(options -> options.setEnableExternalConfiguration(true), GLOBAL_HUB_DEFAULT_MODE);
  }

  /**
   * Initializes the SDK
   *
   * @param dsn The Sentry DSN
   */
  public static void init(final @NotNull String dsn) {
    init(options -> options.setDsn(dsn));
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
    applyOptionsConfiguration(optionsConfiguration, options);
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
    applyOptionsConfiguration(optionsConfiguration, options);
    init(options, globalHubMode);
  }

  private static <T extends SentryOptions> void applyOptionsConfiguration(
      OptionsConfiguration<T> optionsConfiguration, T options) {
    try {
      optionsConfiguration.configure(options);
    } catch (Throwable t) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Error in the 'OptionsConfiguration.configure' callback.", t);
    }
  }

  /**
   * Initializes the SDK with a SentryOptions.
   *
   * @param options options the SentryOptions
   */
  @ApiStatus.Internal
  public static void init(final @NotNull SentryOptions options) {
    init(options, GLOBAL_HUB_DEFAULT_MODE);
  }

  /**
   * Initializes the SDK with a SentryOptions and globalHubMode
   *
   * @param options options the SentryOptions
   * @param globalHubMode the globalHubMode
   */
  private static synchronized void init(
      final @NotNull SentryOptions options, final boolean globalHubMode) {
    if (isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Sentry has been already initialized. Previous configuration will be overwritten.");
    }

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

  @SuppressWarnings("FutureReturnValueIgnored")
  private static boolean initConfigurations(final @NotNull SentryOptions options) {
    if (options.isEnableExternalConfiguration()) {
      options.merge(ExternalOptions.from(PropertiesProviderFactory.create(), options.getLogger()));
    }

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

    // this should be after setting serializers
    final String outboxPath = options.getOutboxPath();
    if (outboxPath != null) {
      final File outboxDir = new File(outboxPath);
      outboxDir.mkdirs();
    } else {
      logger.log(SentryLevel.INFO, "No outbox dir path is defined in options.");
    }

    final String cacheDirPath = options.getCacheDirPath();
    if (cacheDirPath != null) {
      final File cacheDir = new File(cacheDirPath);
      cacheDir.mkdirs();
      final IEnvelopeCache envelopeCache = options.getEnvelopeDiskCache();
      // only overwrite the cache impl if it's not already set
      if (envelopeCache instanceof NoOpEnvelopeCache) {
        options.setEnvelopeDiskCache(EnvelopeCache.create(options));
      }
    }

    final String profilingTracesDirPath = options.getProfilingTracesDirPath();
    if (options.isProfilingEnabled() && profilingTracesDirPath != null) {

      final File profilingTracesDir = new File(profilingTracesDirPath);
      profilingTracesDir.mkdirs();
      final File[] oldTracesDirContent = profilingTracesDir.listFiles();

      options
          .getExecutorService()
          .submit(
              () -> {
                if (oldTracesDirContent == null) return;
                // Method trace files are normally deleted at the end of traces, but if that fails
                // for some reason we try to clear any old files here.
                for (File f : oldTracesDirContent) {
                  FileUtils.deleteRecursively(f);
                }
              });
    }

    final IModulesLoader modulesLoader = options.getModulesLoader();
    // only override the ModulesLoader if it's not already set by Android
    if (modulesLoader instanceof NoOpModulesLoader) {
      options.setModulesLoader(new ResourcesModulesLoader(options.getLogger()));
    }

    final IMainThreadChecker mainThreadChecker = options.getMainThreadChecker();
    // only override the MainThreadChecker if it's not already set by Android
    if (mainThreadChecker instanceof NoOpMainThreadChecker) {
      options.setMainThreadChecker(MainThreadChecker.getInstance());
    }

    if (options.getMemoryCollector() instanceof NoOpMemoryCollector) {
      options.setMemoryCollector(new JavaMemoryCollector());
    }

    return true;
  }

  /** Close the SDK */
  public static synchronized void close() {
    final IHub hub = getCurrentHub();
    mainHub = NoOpHub.getInstance();
    // remove thread local to avoid memory leak
    currentHub.remove();
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
   * @param event The event.
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureEvent(
      final @NotNull SentryEvent event, final @NotNull ScopeCallback callback) {
    return getCurrentHub().captureEvent(event, callback);
  }

  /**
   * Captures the event.
   *
   * @param event the event
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureEvent(
      final @NotNull SentryEvent event, final @Nullable Hint hint) {
    return getCurrentHub().captureEvent(event, hint);
  }

  /**
   * Captures the event.
   *
   * @param event The event.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureEvent(
      final @NotNull SentryEvent event,
      final @Nullable Hint hint,
      final @NotNull ScopeCallback callback) {
    return getCurrentHub().captureEvent(event, hint, callback);
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
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureMessage(
      final @NotNull String message, final @NotNull ScopeCallback callback) {
    return getCurrentHub().captureMessage(message, callback);
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
   * Captures the message.
   *
   * @param message The message to send.
   * @param level The message level.
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureMessage(
      final @NotNull String message,
      final @NotNull SentryLevel level,
      final @NotNull ScopeCallback callback) {
    return getCurrentHub().captureMessage(message, level, callback);
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
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureException(
      final @NotNull Throwable throwable, final @NotNull ScopeCallback callback) {
    return getCurrentHub().captureException(throwable, callback);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureException(
      final @NotNull Throwable throwable, final @Nullable Hint hint) {
    return getCurrentHub().captureException(throwable, hint);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureException(
      final @NotNull Throwable throwable,
      final @Nullable Hint hint,
      final @NotNull ScopeCallback callback) {
    return getCurrentHub().captureException(throwable, hint, callback);
  }

  /**
   * Captures a manually created user feedback and sends it to Sentry.
   *
   * @param userFeedback The user feedback to send to Sentry.
   */
  public static void captureUserFeedback(final @NotNull UserFeedback userFeedback) {
    getCurrentHub().captureUserFeedback(userFeedback);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   * @param hint SDK specific but provides high level information about the origin of the event
   */
  public static void addBreadcrumb(
      final @NotNull Breadcrumb breadcrumb, final @Nullable Hint hint) {
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
   * Creates a Transaction and returns the instance. Started transaction is set on the scope.
   *
   * @param name the transaction name
   * @param operation the operation
   * @return created transaction
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull String name, final @NotNull String operation) {
    return getCurrentHub().startTransaction(name, operation);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param bindToScope if transaction should be bound to scope
   * @return created transaction
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull String name, final @NotNull String operation, final boolean bindToScope) {
    return getCurrentHub().startTransaction(name, operation, bindToScope);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param description the description
   * @return created transaction
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull String name,
      final @NotNull String operation,
      final @Nullable String description) {
    return startTransaction(name, operation, description, false);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param description the description
   * @param bindToScope if transaction should be bound to scope
   * @return created transaction
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull String name,
      final @NotNull String operation,
      final @Nullable String description,
      final boolean bindToScope) {
    final ITransaction transaction = getCurrentHub().startTransaction(name, operation, bindToScope);
    transaction.setDescription(description);
    return transaction;
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param transactionContexts the transaction contexts
   * @return created transaction
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull TransactionContext transactionContexts) {
    return getCurrentHub().startTransaction(transactionContexts);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param transactionContexts the transaction contexts
   * @param bindToScope if transaction should be bound to scope
   * @return created transaction
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull TransactionContext transactionContexts, boolean bindToScope) {
    return getCurrentHub().startTransaction(transactionContexts, bindToScope);
  }

  /**
   * Creates a Transaction and returns the instance. Based on the passed sampling context the
   * decision if transaction is sampled will be taken by {@link TracesSampler}.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param customSamplingContext the sampling context
   * @return created transaction.
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull String name,
      final @NotNull String operation,
      final @NotNull CustomSamplingContext customSamplingContext) {
    return getCurrentHub().startTransaction(name, operation, customSamplingContext);
  }

  /**
   * Creates a Transaction and returns the instance. Based on the passed sampling context the
   * decision if transaction is sampled will be taken by {@link TracesSampler}.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param customSamplingContext the sampling context
   * @param bindToScope if transaction should be bound to scope
   * @return created transaction.
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull String name,
      final @NotNull String operation,
      final @NotNull CustomSamplingContext customSamplingContext,
      final boolean bindToScope) {
    return getCurrentHub().startTransaction(name, operation, customSamplingContext, bindToScope);
  }

  /**
   * Creates a Transaction and returns the instance. Based on the passed transaction and sampling
   * contexts the decision if transaction is sampled will be taken by {@link TracesSampler}.
   *
   * @param transactionContexts the transaction context
   * @param customSamplingContext the sampling context
   * @return created transaction.
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull TransactionContext transactionContexts,
      final @NotNull CustomSamplingContext customSamplingContext) {
    return getCurrentHub().startTransaction(transactionContexts, customSamplingContext);
  }

  /**
   * Creates a Transaction and returns the instance. Based on the passed transaction and sampling
   * contexts the decision if transaction is sampled will be taken by {@link TracesSampler}.
   *
   * @param transactionContexts the transaction context
   * @param customSamplingContext the sampling context
   * @param bindToScope if transaction should be bound to scope
   * @return created transaction.
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull TransactionContext transactionContexts,
      final @Nullable CustomSamplingContext customSamplingContext,
      final boolean bindToScope) {
    return getCurrentHub()
        .startTransaction(transactionContexts, customSamplingContext, bindToScope);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param transactionContext the transaction context
   * @param transactionOptions options for the transaction
   * @return created transaction.
   */
  @ApiStatus.Internal
  public static @NotNull ITransaction startTransaction(
      final @NotNull TransactionContext transactionContext,
      final @NotNull TransactionOptions transactionOptions) {
    return getCurrentHub().startTransaction(transactionContext, transactionOptions);
  }

  /**
   * Returns trace header of active transaction or {@code null} if no transaction is active.
   *
   * @return trace header or null
   */
  public static @Nullable SentryTraceHeader traceHeaders() {
    return getCurrentHub().traceHeaders();
  }

  /**
   * Gets the current active transaction or span.
   *
   * @return the active span or null when no active transaction is running
   */
  public static @Nullable ISpan getSpan() {
    return getCurrentHub().getSpan();
  }

  /**
   * Returns if the App has crashed (Process has terminated) during the last run. It only returns
   * true or false if offline caching {{@link SentryOptions#getCacheDirPath()} } is set with a valid
   * dir.
   *
   * <p>If the call to this method is early in the App lifecycle and the SDK could not check if the
   * App has crashed in the background, the check is gonna do IO in the calling thread.
   *
   * @return true if App has crashed, false otherwise, and null if not evaluated yet
   */
  public static @Nullable Boolean isCrashedLastRun() {
    return getCurrentHub().isCrashedLastRun();
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
