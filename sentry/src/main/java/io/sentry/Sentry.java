package io.sentry;

import io.sentry.backpressure.BackpressureMonitor;
import io.sentry.backpressure.NoOpBackpressureMonitor;
import io.sentry.cache.EnvelopeCache;
import io.sentry.cache.IEnvelopeCache;
import io.sentry.cache.PersistingScopeObserver;
import io.sentry.config.PropertiesProviderFactory;
import io.sentry.internal.debugmeta.NoOpDebugMetaLoader;
import io.sentry.internal.debugmeta.ResourcesDebugMetaLoader;
import io.sentry.internal.modules.CompositeModulesLoader;
import io.sentry.internal.modules.IModulesLoader;
import io.sentry.internal.modules.ManifestModulesLoader;
import io.sentry.internal.modules.NoOpModulesLoader;
import io.sentry.internal.modules.ResourcesModulesLoader;
import io.sentry.logger.ILoggerApi;
import io.sentry.opentelemetry.OpenTelemetryUtil;
import io.sentry.protocol.Feedback;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.transport.NoOpEnvelopeCache;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.DebugMetaPropertiesApplier;
import io.sentry.util.FileUtils;
import io.sentry.util.InitUtil;
import io.sentry.util.LoadClass;
import io.sentry.util.Platform;
import io.sentry.util.SentryRandom;
import io.sentry.util.thread.IThreadChecker;
import io.sentry.util.thread.NoOpThreadChecker;
import io.sentry.util.thread.ThreadChecker;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sentry SDK main API entry point */
public final class Sentry {

  private Sentry() {}

  // TODO logger?
  private static volatile @NotNull IScopesStorage scopesStorage = NoOpScopesStorage.getInstance();

  /** The root Scopes or NoOp if Sentry is disabled. */
  private static volatile @NotNull IScopes rootScopes = NoOpScopes.getInstance();

  /**
   * This initializes global scope with default options. Options will later be replaced on
   * Sentry.init
   *
   * <p>For Android options will also be (temporarily) replaced by SentryAndroid static block.
   */
  // TODO https://github.com/getsentry/sentry-java/issues/2541
  private static final @NotNull IScope globalScope = new Scope(SentryOptions.empty());

  /** Default value for globalHubMode is false */
  private static final boolean GLOBAL_HUB_DEFAULT_MODE = false;

  /** whether to use a single (global) Scopes as opposed to one per thread. */
  private static volatile boolean globalHubMode = GLOBAL_HUB_DEFAULT_MODE;

  @ApiStatus.Internal
  public static final @NotNull String APP_START_PROFILING_CONFIG_FILE_NAME =
      "app_start_profiling_config";

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  /** Timestamp used to check old profiles to delete. */
  private static final long classCreationTimestamp = System.currentTimeMillis();

  private static final AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  /**
   * Returns the current (threads) hub, if none, clones the rootScopes and returns it.
   *
   * @deprecated please use {@link Sentry#getCurrentScopes()} instead
   * @return the hub
   */
  @ApiStatus.Internal // exposed for the coroutines integration in SentryContext
  @SuppressWarnings("deprecation")
  @Deprecated
  public static @NotNull IHub getCurrentHub() {
    return new HubScopesWrapper(getCurrentScopes());
  }

  @ApiStatus.Internal // exposed for the coroutines integration in SentryContext
  @SuppressWarnings("deprecation")
  public static @NotNull IScopes getCurrentScopes() {
    if (globalHubMode) {
      return rootScopes;
    }
    @Nullable IScopes scopes = getScopesStorage().get();
    if (scopes == null || scopes.isNoOp()) {
      scopes = rootScopes.forkedScopes("getCurrentScopes");
      getScopesStorage().set(scopes);
    }
    return scopes;
  }

  private static @NotNull IScopesStorage getScopesStorage() {
    return scopesStorage;
  }

  /**
   * Returns a new Scopes which is cloned from the rootScopes.
   *
   * @return the forked scopes
   */
  @ApiStatus.Internal
  public static @NotNull IScopes forkedRootScopes(final @NotNull String creator) {
    if (globalHubMode) {
      return rootScopes;
    }
    return rootScopes.forkedScopes(creator);
  }

  public static @NotNull IScopes forkedScopes(final @NotNull String creator) {
    return getCurrentScopes().forkedScopes(creator);
  }

  public static @NotNull IScopes forkedCurrentScope(final @NotNull String creator) {
    return getCurrentScopes().forkedCurrentScope(creator);
  }

  /**
   * @deprecated please use {@link Sentry#setCurrentScopes} instead.
   */
  @ApiStatus.Internal // exposed for the coroutines integration in SentryContext
  @Deprecated
  @SuppressWarnings({"deprecation", "InlineMeSuggester"})
  public static @NotNull ISentryLifecycleToken setCurrentHub(final @NotNull IHub hub) {
    return setCurrentScopes(hub);
  }

  @ApiStatus.Internal // exposed for the coroutines integration in SentryContext
  public static @NotNull ISentryLifecycleToken setCurrentScopes(final @NotNull IScopes scopes) {
    return getScopesStorage().set(scopes);
  }

  public static @NotNull IScope getGlobalScope() {
    return globalScope;
  }

  /**
   * Check if Sentry is enabled/active.
   *
   * @return true if its enabled or false otherwise.
   */
  public static boolean isEnabled() {
    return getCurrentScopes().isEnabled();
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
      throws IllegalAccessException,
          InstantiationException,
          NoSuchMethodException,
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
      throws IllegalAccessException,
          InstantiationException,
          NoSuchMethodException,
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
  @SuppressWarnings({
    "deprecation",
    "Convert2MethodRef",
    "FutureReturnValueIgnored"
  }) // older AGP versions do not support method references
  private static void init(final @NotNull SentryOptions options, final boolean globalHubMode) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (!options.getClass().getName().equals("io.sentry.android.core.SentryAndroidOptions")
          && Platform.isAndroid()) {
        throw new IllegalArgumentException(
            "You are running Android. Please, use SentryAndroid.init. "
                + options.getClass().getName());
      }

      if (!preInitConfigurations(options)) {
        return;
      }

      final @Nullable Boolean globalHubModeFromOptions = options.isGlobalHubMode();
      final boolean globalHubModeToUse =
          globalHubModeFromOptions != null ? globalHubModeFromOptions : globalHubMode;
      options
          .getLogger()
          .log(SentryLevel.INFO, "GlobalHubMode: '%s'", String.valueOf(globalHubModeToUse));
      Sentry.globalHubMode = globalHubModeToUse;
      initFatalLogger(options);
      final boolean shouldInit =
          InitUtil.shouldInit(globalScope.getOptions(), options, isEnabled());
      if (shouldInit) {
        if (isEnabled()) {
          options
              .getLogger()
              .log(
                  SentryLevel.WARNING,
                  "Sentry has been already initialized. Previous configuration will be overwritten.");
        }

        // load lazy fields of the options in a separate thread
        try {
          options.getExecutorService().submit(() -> options.loadLazyFields());
        } catch (RejectedExecutionException e) {
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Failed to call the executor. Lazy fields will not be loaded. Did you call Sentry.close()?",
                  e);
        }

        final IScopes scopes = getCurrentScopes();
        scopes.close(true);

        globalScope.replaceOptions(options);

        final IScope rootScope = new Scope(options);
        final IScope rootIsolationScope = new Scope(options);
        rootScopes = new Scopes(rootScope, rootIsolationScope, globalScope, "Sentry.init");

        initLogger(options);
        initForOpenTelemetryMaybe(options);
        getScopesStorage().set(rootScopes);
        initConfigurations(options);

        globalScope.bindClient(new SentryClient(options));

        // If the executorService passed in the init is the same that was previously closed, we have
        // to
        // set a new one
        if (options.getExecutorService().isClosed()) {
          options.setExecutorService(new SentryExecutorService(options));
          options.getExecutorService().prewarm();
        }
        // when integrations are registered on Scopes ctor and async integrations are fired,
        // it might and actually happened that integrations called captureSomething
        // and Scopes was still NoOp.
        // Registering integrations here make sure that Scopes is already created.
        for (final Integration integration : options.getIntegrations()) {
          try {
            integration.register(ScopesAdapter.getInstance(), options);
          } catch (Throwable t) {
            options
                .getLogger()
                .log(
                    SentryLevel.WARNING,
                    "Failed to register the integration " + integration.getClass().getName(),
                    t);
          }
        }

        notifyOptionsObservers(options);

        finalizePreviousSession(options, ScopesAdapter.getInstance());

        handleAppStartProfilingConfig(options, options.getExecutorService());

        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Using openTelemetryMode %s", options.getOpenTelemetryMode());
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Using span factory %s",
                options.getSpanFactory().getClass().getName());
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Using scopes storage %s", scopesStorage.getClass().getName());
      } else {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "This init call has been ignored due to priority being too low.");
      }
    }
  }

  private static void initForOpenTelemetryMaybe(SentryOptions options) {
    OpenTelemetryUtil.updateOpenTelemetryModeIfAuto(options, new LoadClass());
    if (SentryOpenTelemetryMode.OFF == options.getOpenTelemetryMode()) {
      options.setSpanFactory(new DefaultSpanFactory());
      //    } else {
      // enabling this causes issues with agentless where OTel spans seem to be randomly ended
      //      options.setSpanFactory(SpanFactoryFactory.create(new LoadClass(),
      // NoOpLogger.getInstance()));
    }
    initScopesStorage(options);
    OpenTelemetryUtil.applyIgnoredSpanOrigins(options);
  }

  private static void initLogger(final @NotNull SentryOptions options) {
    if (options.isDebug() && options.getLogger() instanceof NoOpLogger) {
      options.setLogger(new SystemOutLogger());
    }
  }

  private static void initFatalLogger(final @NotNull SentryOptions options) {
    if (options.getFatalLogger() instanceof NoOpLogger) {
      options.setFatalLogger(new SystemOutLogger());
    }
  }

  private static void initScopesStorage(SentryOptions options) {
    getScopesStorage().close();
    if (SentryOpenTelemetryMode.OFF == options.getOpenTelemetryMode()) {
      scopesStorage = new DefaultScopesStorage();
    } else {
      scopesStorage = ScopesStorageFactory.create(new LoadClass(), NoOpLogger.getInstance());
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private static void handleAppStartProfilingConfig(
      final @NotNull SentryOptions options,
      final @NotNull ISentryExecutorService sentryExecutorService) {
    try {
      sentryExecutorService.submit(
          () -> {
            final String cacheDirPath = options.getCacheDirPathWithoutDsn();
            if (cacheDirPath != null) {
              final @NotNull File appStartProfilingConfigFile =
                  new File(cacheDirPath, APP_START_PROFILING_CONFIG_FILE_NAME);
              try {
                // We always delete the config file for app start profiling
                FileUtils.deleteRecursively(appStartProfilingConfigFile);
                if (!options.isEnableAppStartProfiling() && !options.isStartProfilerOnAppStart()) {
                  return;
                }
                // isStartProfilerOnAppStart doesn't need tracing, as it can be started/stopped
                // manually
                if (!options.isStartProfilerOnAppStart() && !options.isTracingEnabled()) {
                  options
                      .getLogger()
                      .log(
                          SentryLevel.INFO,
                          "Tracing is disabled and app start profiling will not start.");
                  return;
                }
                if (appStartProfilingConfigFile.createNewFile()) {
                  // If old app start profiling is false, it means the transaction will not be
                  // sampled, but we create the file anyway to allow continuous profiling on app
                  // start
                  final @NotNull TracesSamplingDecision appStartSamplingDecision =
                      options.isEnableAppStartProfiling()
                          ? sampleAppStartProfiling(options)
                          : new TracesSamplingDecision(false);
                  final @NotNull SentryAppStartProfilingOptions appStartProfilingOptions =
                      new SentryAppStartProfilingOptions(options, appStartSamplingDecision);
                  try (final OutputStream outputStream =
                          new FileOutputStream(appStartProfilingConfigFile);
                      final Writer writer =
                          new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8))) {
                    options.getSerializer().serialize(appStartProfilingOptions, writer);
                  }
                }
              } catch (Throwable e) {
                options
                    .getLogger()
                    .log(
                        SentryLevel.ERROR, "Unable to create app start profiling config file. ", e);
              }
            }
          });
    } catch (Throwable e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Failed to call the executor. App start profiling config will not be changed. Did you call Sentry.close()?",
              e);
    }
  }

  private static @NotNull TracesSamplingDecision sampleAppStartProfiling(
      final @NotNull SentryOptions options) {
    TransactionContext appStartTransactionContext = new TransactionContext("app.launch", "profile");
    appStartTransactionContext.setForNextAppStart(true);
    SamplingContext appStartSamplingContext =
        new SamplingContext(
            appStartTransactionContext, null, SentryRandom.current().nextDouble(), null);
    return options.getInternalTracesSampler().sample(appStartSamplingContext);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private static void finalizePreviousSession(
      final @NotNull SentryOptions options, final @NotNull IScopes scopes) {
    // enqueue a task to finalize previous session. Since the executor
    // is single-threaded, this task will be enqueued sequentially after all integrations that have
    // to modify the previous session have done their work, even if they do that async.
    try {
      options.getExecutorService().submit(new PreviousSessionFinalizer(options, scopes));
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.DEBUG, "Failed to finalize previous session.", e);
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private static void notifyOptionsObservers(final @NotNull SentryOptions options) {
    // enqueue a task to trigger the static options change for the observers. Since the executor
    // is single-threaded, this task will be enqueued sequentially after all integrations that rely
    // on the observers have done their work, even if they do that async.
    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                // for static things like sentry options we can immediately trigger observers
                for (final IOptionsObserver observer : options.getOptionsObservers()) {
                  observer.setRelease(options.getRelease());
                  observer.setProguardUuid(options.getProguardUuid());
                  observer.setSdkVersion(options.getSdkVersion());
                  observer.setDist(options.getDist());
                  observer.setEnvironment(options.getEnvironment());
                  observer.setTags(options.getTags());
                  observer.setReplayErrorSampleRate(
                      options.getSessionReplay().getOnErrorSampleRate());
                }

                // since it's a new SDK init we clean up persisted scope values before serializing
                // new ones, so they are not making it to the new events if they were e.g. disabled
                // (e.g. replayId) or are simply irrelevant (e.g. breadcrumbs). NOTE: this happens
                // after the integrations relying on those values are done with processing them.
                final @Nullable PersistingScopeObserver scopeCache =
                    options.findPersistingScopeObserver();
                if (scopeCache != null) {
                  scopeCache.resetCache();
                }
              });
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.DEBUG, "Failed to notify options observers.", e);
    }
  }

  private static boolean preInitConfigurations(final @NotNull SentryOptions options) {
    if (options.isEnableExternalConfiguration()) {
      options.merge(ExternalOptions.from(PropertiesProviderFactory.create(), options.getLogger()));
    }

    final String dsn = options.getDsn();

    if (!options.isEnabled() || (dsn != null && dsn.isEmpty())) {
      close();
      return false;
    } else if (dsn == null) {
      throw new IllegalArgumentException(
          "DSN is required. Use empty string or set enabled to false in SentryOptions to disable SDK.");
    }

    // This creates the DSN object and performs some checks
    options.retrieveParsedDsn();

    return true;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private static void initConfigurations(final @NotNull SentryOptions options) {
    final @NotNull ILogger logger = options.getLogger();
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
    if ((options.isProfilingEnabled() || options.isContinuousProfilingEnabled())
        && profilingTracesDirPath != null) {

      final File profilingTracesDir = new File(profilingTracesDirPath);
      profilingTracesDir.mkdirs();

      try {
        options
            .getExecutorService()
            .submit(
                () -> {
                  final File[] oldTracesDirContent = profilingTracesDir.listFiles();
                  if (oldTracesDirContent == null) return;
                  // Method trace files are normally deleted at the end of traces, but if that fails
                  // for some reason we try to clear any old files here.
                  for (File f : oldTracesDirContent) {
                    // We delete files 5 minutes older than class creation to account for app
                    // start profiles, as an app start profile could have a lower creation date.
                    if (f.lastModified() < classCreationTimestamp - TimeUnit.MINUTES.toMillis(5)) {
                      FileUtils.deleteRecursively(f);
                    }
                  }
                });
      } catch (RejectedExecutionException e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "Failed to call the executor. Old profiles will not be deleted. Did you call Sentry.close()?",
                e);
      }
    }

    final @NotNull IModulesLoader modulesLoader = options.getModulesLoader();
    if (!options.isSendModules()) {
      options.setModulesLoader(NoOpModulesLoader.getInstance());
    } else if (modulesLoader instanceof NoOpModulesLoader) {
      options.setModulesLoader(
          new CompositeModulesLoader(
              Arrays.asList(
                  new ManifestModulesLoader(options.getLogger()),
                  new ResourcesModulesLoader(options.getLogger())),
              options.getLogger()));
    }

    if (options.getDebugMetaLoader() instanceof NoOpDebugMetaLoader) {
      options.setDebugMetaLoader(new ResourcesDebugMetaLoader(options.getLogger()));
    }
    final @Nullable List<Properties> propertiesList = options.getDebugMetaLoader().loadDebugMeta();
    DebugMetaPropertiesApplier.apply(options, propertiesList);

    final IThreadChecker threadChecker = options.getThreadChecker();
    // only override the ThreadChecker if it's not already set by Android
    if (threadChecker instanceof NoOpThreadChecker) {
      options.setThreadChecker(ThreadChecker.getInstance());
    }

    if (options.getPerformanceCollectors().isEmpty()) {
      options.addPerformanceCollector(new JavaMemoryCollector());
    }

    if (options.isEnableBackpressureHandling() && Platform.isJvm()) {
      if (options.getBackpressureMonitor() instanceof NoOpBackpressureMonitor) {
        options.setBackpressureMonitor(
            new BackpressureMonitor(options, ScopesAdapter.getInstance()));
      }
      options.getBackpressureMonitor().start();
    }
  }

  /** Close the SDK */
  public static void close() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final IScopes scopes = getCurrentScopes();
      rootScopes = NoOpScopes.getInstance();
      // remove thread local to avoid memory leak
      getScopesStorage().close();
      scopes.close(false);
    }
  }

  /**
   * Captures the event.
   *
   * @param event the event
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureEvent(final @NotNull SentryEvent event) {
    return getCurrentScopes().captureEvent(event);
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
    return getCurrentScopes().captureEvent(event, callback);
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
    return getCurrentScopes().captureEvent(event, hint);
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
    return getCurrentScopes().captureEvent(event, hint, callback);
  }

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureMessage(final @NotNull String message) {
    return getCurrentScopes().captureMessage(message);
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
    return getCurrentScopes().captureMessage(message, callback);
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
    return getCurrentScopes().captureMessage(message, level);
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
    return getCurrentScopes().captureMessage(message, level, callback);
  }

  /**
   * Captures the feedback.
   *
   * @param feedback The feedback to send.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureFeedback(final @NotNull Feedback feedback) {
    return getCurrentScopes().captureFeedback(feedback);
  }

  /**
   * Captures the feedback.
   *
   * @param feedback The feedback to send.
   * @param hint An optional hint to be applied to the event.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureFeedback(
      final @NotNull Feedback feedback, final @Nullable Hint hint) {
    return getCurrentScopes().captureFeedback(feedback, hint);
  }

  /**
   * Captures the feedback.
   *
   * @param feedback The feedback to send.
   * @param hint An optional hint to be applied to the event.
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureFeedback(
      final @NotNull Feedback feedback,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback callback) {
    return getCurrentScopes().captureFeedback(feedback, hint, callback);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @return The Id (SentryId object) of the event
   */
  public static @NotNull SentryId captureException(final @NotNull Throwable throwable) {
    return getCurrentScopes().captureException(throwable);
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
    return getCurrentScopes().captureException(throwable, callback);
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
    return getCurrentScopes().captureException(throwable, hint);
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
    return getCurrentScopes().captureException(throwable, hint, callback);
  }

  /**
   * Captures a manually created user feedback and sends it to Sentry.
   *
   * @param userFeedback The user feedback to send to Sentry.
   */
  public static void captureUserFeedback(final @NotNull UserFeedback userFeedback) {
    getCurrentScopes().captureUserFeedback(userFeedback);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   * @param hint SDK specific but provides high level information about the origin of the event
   */
  public static void addBreadcrumb(
      final @NotNull Breadcrumb breadcrumb, final @Nullable Hint hint) {
    getCurrentScopes().addBreadcrumb(breadcrumb, hint);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   */
  public static void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    getCurrentScopes().addBreadcrumb(breadcrumb);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param message rendered as text and the whitespace is preserved.
   */
  public static void addBreadcrumb(final @NotNull String message) {
    getCurrentScopes().addBreadcrumb(message);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param message rendered as text and the whitespace is preserved.
   * @param category Categories are dotted strings that indicate what the crumb is or where it comes
   *     from.
   */
  public static void addBreadcrumb(final @NotNull String message, final @NotNull String category) {
    getCurrentScopes().addBreadcrumb(message, category);
  }

  /**
   * Sets the level of all events sent within current Scope
   *
   * @param level the Sentry level
   */
  public static void setLevel(final @Nullable SentryLevel level) {
    getCurrentScopes().setLevel(level);
  }

  /**
   * Sets the name of the current transaction to the current Scope.
   *
   * @param transaction the transaction
   */
  public static void setTransaction(final @Nullable String transaction) {
    getCurrentScopes().setTransaction(transaction);
  }

  /**
   * Shallow merges user configuration (email, username, etc) to the current Scope.
   *
   * @param user the user
   */
  public static void setUser(final @Nullable User user) {
    getCurrentScopes().setUser(user);
  }

  /**
   * Sets the fingerprint to group specific events together to the current Scope.
   *
   * @param fingerprint the fingerprints
   */
  public static void setFingerprint(final @NotNull List<String> fingerprint) {
    getCurrentScopes().setFingerprint(fingerprint);
  }

  /** Deletes current breadcrumbs from the current scope. */
  public static void clearBreadcrumbs() {
    getCurrentScopes().clearBreadcrumbs();
  }

  /**
   * Sets the tag to a string value to the current Scope, overwriting a potential previous value
   *
   * @param key the key
   * @param value the value
   */
  public static void setTag(final @Nullable String key, final @Nullable String value) {
    getCurrentScopes().setTag(key, value);
  }

  /**
   * Removes the tag to a string value to the current Scope
   *
   * @param key the key
   */
  public static void removeTag(final @Nullable String key) {
    getCurrentScopes().removeTag(key);
  }

  /**
   * Sets the extra key to an arbitrary value to the current Scope, overwriting a potential previous
   * value
   *
   * @param key the key
   * @param value the value
   */
  public static void setExtra(final @Nullable String key, final @Nullable String value) {
    getCurrentScopes().setExtra(key, value);
  }

  /**
   * Removes the extra key to an arbitrary value to the current Scope
   *
   * @param key the key
   */
  public static void removeExtra(final @Nullable String key) {
    getCurrentScopes().removeExtra(key);
  }

  /**
   * Last event id recorded in the current scope
   *
   * @return last SentryId
   */
  public static @NotNull SentryId getLastEventId() {
    return getCurrentScopes().getLastEventId();
  }

  /** Pushes a new scope while inheriting the current scope's data. */
  public static @NotNull ISentryLifecycleToken pushScope() {
    // pushScope is no-op in global hub mode
    if (!globalHubMode) {
      return getCurrentScopes().pushScope();
    }
    return NoOpScopesLifecycleToken.getInstance();
  }

  /** Pushes a new isolation and current scope while inheriting the current scope's data. */
  public static @NotNull ISentryLifecycleToken pushIsolationScope() {
    // pushScope is no-op in global hub mode
    if (!globalHubMode) {
      return getCurrentScopes().pushIsolationScope();
    }
    return NoOpScopesLifecycleToken.getInstance();
  }

  /**
   * Removes the first scope and restores its parent.
   *
   * @deprecated please call {@link ISentryLifecycleToken#close()} on the token returned by {@link
   *     Sentry#pushScope()} or {@link Sentry#pushIsolationScope()} instead.
   */
  @Deprecated
  public static void popScope() {
    // popScope is no-op in global hub mode
    if (!globalHubMode) {
      getCurrentScopes().popScope();
    }
  }

  /**
   * Runs the callback with a new current scope which gets dropped at the end
   *
   * @param callback the callback
   */
  public static void withScope(final @NotNull ScopeCallback callback) {
    getCurrentScopes().withScope(callback);
  }

  /**
   * Runs the callback with a new isolation scope which gets dropped at the end. Current scope is
   * also forked.
   *
   * @param callback the callback
   */
  public static void withIsolationScope(final @NotNull ScopeCallback callback) {
    getCurrentScopes().withIsolationScope(callback);
  }

  /**
   * Configures the scope through the callback.
   *
   * @param callback The configure scope callback.
   */
  public static void configureScope(final @NotNull ScopeCallback callback) {
    configureScope(null, callback);
  }

  /**
   * Configures the scope through the callback.
   *
   * @param callback The configure scope callback.
   */
  public static void configureScope(
      final @Nullable ScopeType scopeType, final @NotNull ScopeCallback callback) {
    getCurrentScopes().configureScope(scopeType, callback);
  }

  /**
   * Binds a different client to the current Scopes
   *
   * @param client the client.
   */
  public static void bindClient(final @NotNull ISentryClient client) {
    getCurrentScopes().bindClient(client);
  }

  public static boolean isHealthy() {
    return getCurrentScopes().isHealthy();
  }

  /**
   * Flushes events queued up to the current Scopes.
   *
   * @param timeoutMillis time in milliseconds
   */
  public static void flush(final long timeoutMillis) {
    getCurrentScopes().flush(timeoutMillis);
  }

  /** Starts a new session. If there's a running session, it ends it before starting the new one. */
  public static void startSession() {
    getCurrentScopes().startSession();
  }

  /** Ends the current session */
  public static void endSession() {
    getCurrentScopes().endSession();
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
    return getCurrentScopes().startTransaction(name, operation);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param transactionOptions options for the transaction
   * @return created transaction
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull String name,
      final @NotNull String operation,
      final @NotNull TransactionOptions transactionOptions) {
    return getCurrentScopes().startTransaction(name, operation, transactionOptions);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param description the description
   * @param transactionOptions options for the transaction
   * @return created transaction
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull String name,
      final @NotNull String operation,
      final @Nullable String description,
      final @NotNull TransactionOptions transactionOptions) {
    final ITransaction transaction =
        getCurrentScopes().startTransaction(name, operation, transactionOptions);
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
    return getCurrentScopes().startTransaction(transactionContexts);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param transactionContext the transaction context
   * @param transactionOptions options for the transaction
   * @return created transaction.
   */
  public static @NotNull ITransaction startTransaction(
      final @NotNull TransactionContext transactionContext,
      final @NotNull TransactionOptions transactionOptions) {
    return getCurrentScopes().startTransaction(transactionContext, transactionOptions);
  }

  /** Starts the continuous profiler, if enabled. */
  @ApiStatus.Experimental
  public static void startProfiler() {
    getCurrentScopes().startProfiler();
  }

  /** Stops the continuous profiler, if enabled. */
  @ApiStatus.Experimental
  public static void stopProfiler() {
    getCurrentScopes().stopProfiler();
  }

  /**
   * Gets the current active transaction or span.
   *
   * @return the active span or null when no active transaction is running. In case of
   *     globalHubMode=true, always the active transaction is returned, rather than the last active
   *     span.
   */
  public static @Nullable ISpan getSpan() {
    if (globalHubMode && Platform.isAndroid()) {
      return getCurrentScopes().getTransaction();
    } else {
      return getCurrentScopes().getSpan();
    }
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
    return getCurrentScopes().isCrashedLastRun();
  }

  /**
   * Report a screen has been fully loaded. That means all data needed by the UI was loaded. If
   * time-to-full-display tracing {{@link SentryOptions#isEnableTimeToFullDisplayTracing()} } is
   * disabled this call is ignored.
   *
   * <p>This method is safe to be called multiple times. If the time-to-full-display span is already
   * finished, this call will be ignored.
   */
  public static void reportFullyDisplayed() {
    getCurrentScopes().reportFullyDisplayed();
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

  /**
   * Continue a trace based on HTTP header values. If no "sentry-trace" header is provided a random
   * trace ID and span ID is created.
   *
   * @param sentryTrace "sentry-trace" header
   * @param baggageHeaders "baggage" headers
   * @return a transaction context for starting a transaction or null if performance is disabled
   */
  // return TransactionContext (if performance enabled) or null (if performance disabled)
  public static @Nullable TransactionContext continueTrace(
      final @Nullable String sentryTrace, final @Nullable List<String> baggageHeaders) {
    return getCurrentScopes().continueTrace(sentryTrace, baggageHeaders);
  }

  /**
   * Returns the "sentry-trace" header that allows tracing across services. Can also be used in
   * &lt;meta&gt; HTML tags. Also see {@link Sentry#getBaggage()}.
   *
   * @return sentry trace header or null
   */
  public static @Nullable SentryTraceHeader getTraceparent() {
    return getCurrentScopes().getTraceparent();
  }

  /**
   * Returns the "baggage" header that allows tracing across services. Can also be used in
   * &lt;meta&gt; HTML tags. Also see {@link Sentry#getTraceparent()}.
   *
   * @return baggage header or null
   */
  public static @Nullable BaggageHeader getBaggage() {
    return getCurrentScopes().getBaggage();
  }

  @ApiStatus.Experimental
  public static @NotNull SentryId captureCheckIn(final @NotNull CheckIn checkIn) {
    return getCurrentScopes().captureCheckIn(checkIn);
  }

  @ApiStatus.Experimental
  @NotNull
  public static ILoggerApi logger() {
    return getCurrentScopes().logger();
  }

  @NotNull
  public static IReplayApi replay() {
    return getCurrentScopes().getScope().getOptions().getReplayController();
  }

  public static void showUserFeedbackDialog() {
    showUserFeedbackDialog(null);
  }

  public static void showUserFeedbackDialog(
      final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator) {
    showUserFeedbackDialog(null, configurator);
  }

  public static void showUserFeedbackDialog(
      final @Nullable SentryId associatedEventId,
      final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator) {
    final @NotNull SentryOptions options = getCurrentScopes().getOptions();
    options.getFeedbackOptions().getDialogHandler().showDialog(associatedEventId, configurator);
  }
}
