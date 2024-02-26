package io.sentry;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.backpressure.IBackpressureMonitor;
import io.sentry.backpressure.NoOpBackpressureMonitor;
import io.sentry.cache.IEnvelopeCache;
import io.sentry.clientreport.ClientReportRecorder;
import io.sentry.clientreport.IClientReportRecorder;
import io.sentry.clientreport.NoOpClientReportRecorder;
import io.sentry.internal.debugmeta.IDebugMetaLoader;
import io.sentry.internal.debugmeta.NoOpDebugMetaLoader;
import io.sentry.internal.gestures.GestureTargetLocator;
import io.sentry.internal.modules.IModulesLoader;
import io.sentry.internal.modules.NoOpModulesLoader;
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryTransaction;
import io.sentry.transport.ITransport;
import io.sentry.transport.ITransportGate;
import io.sentry.transport.NoOpEnvelopeCache;
import io.sentry.transport.NoOpTransportGate;
import io.sentry.util.Platform;
import io.sentry.util.SampleRateUtils;
import io.sentry.util.StringUtils;
import io.sentry.util.thread.IMainThreadChecker;
import io.sentry.util.thread.NoOpMainThreadChecker;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.net.ssl.SSLSocketFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Sentry SDK options */
@Open
public class SentryOptions {

  @ApiStatus.Internal public static final @NotNull String DEFAULT_PROPAGATION_TARGETS = ".*";

  /** Default Log level if not specified Default is DEBUG */
  static final SentryLevel DEFAULT_DIAGNOSTIC_LEVEL = SentryLevel.DEBUG;

  /**
   * Default value for {@link SentryEvent#getEnvironment()} set when {@link SentryOptions} do not
   * have the environment field set.
   */
  private static final String DEFAULT_ENVIRONMENT = "production";

  /**
   * Are callbacks that run for every event. They can either return a new event which in most cases
   * means just adding data OR return null in case the event will be dropped and not sent.
   */
  private final @NotNull List<EventProcessor> eventProcessors = new CopyOnWriteArrayList<>();

  /** Exceptions that once captured will not be sent to Sentry as {@link SentryEvent}. */
  private final @NotNull Set<Class<? extends Throwable>> ignoredExceptionsForType =
      new CopyOnWriteArraySet<>();

  /**
   * Code that provides middlewares, bindings or hooks into certain frameworks or environments,
   * along with code that inserts those bindings and activates them.
   */
  private final @NotNull List<Integration> integrations = new CopyOnWriteArrayList<>();

  /** List of bundle IDs representing source bundles. */
  private final @NotNull Set<String> bundleIds = new CopyOnWriteArraySet<>();

  /**
   * The DSN tells the SDK where to send the events to. If this value is not provided, the SDK will
   * just not send any events.
   */
  private @Nullable String dsn;

  /** dsnHash is used as a subfolder of cacheDirPath to isolate events when rotating DSNs */
  private @Nullable String dsnHash;

  /**
   * Controls how many seconds to wait before shutting down. Sentry SDKs send events from a
   * background queue and this queue is given a certain amount to drain pending events Default is
   * 2000 = 2s
   */
  private long shutdownTimeoutMillis = 2000; // 2s

  /**
   * Controls how many seconds to wait before flushing down. Sentry SDKs cache events from a
   * background queue and this queue is given a certain amount to drain pending events Default is
   * 15000 = 15s
   */
  private long flushTimeoutMillis = 15000; // 15s

  /**
   * Controls how many seconds to wait before flushing previous session. Sentry SDKs finalizes
   * unfinished sessions from a background queue and this queue is given a certain amount to drain
   * sessions. Default is 15000 = 15s
   */
  private long sessionFlushTimeoutMillis = 15000; // 15s

  /**
   * Turns debug mode on or off. If debug is enabled SDK will attempt to print out useful debugging
   * information if something goes wrong. Default is disabled.
   */
  private boolean debug;

  /** Logger interface to log useful debugging information if debug is enabled */
  private @NotNull ILogger logger = NoOpLogger.getInstance();

  /** minimum LogLevel to be used if debug is enabled */
  private @NotNull SentryLevel diagnosticLevel = DEFAULT_DIAGNOSTIC_LEVEL;

  /** Envelope reader interface */
  private @NotNull IEnvelopeReader envelopeReader = new EnvelopeReader(new JsonSerializer(this));

  /** Serializer interface to serialize/deserialize json events */
  private @NotNull ISerializer serializer = new JsonSerializer(this);

  /** Max depth when serializing object graphs with reflection. * */
  private int maxDepth = 100;

  /**
   * Sentry client name used for the HTTP authHeader and userAgent eg
   * sentry.{language}.{platform}/{version} eg sentry.java.android/2.0.0 would be a valid case
   */
  private @Nullable String sentryClientName;

  /**
   * This function is called with an SDK specific event object and can return a modified event
   * object or nothing to skip reporting the event
   */
  private @Nullable BeforeSendCallback beforeSend;

  /**
   * This function is called with an SDK specific transaction object and can return a modified
   * transaction object or nothing to skip reporting the transaction
   */
  private @Nullable BeforeSendTransactionCallback beforeSendTransaction;

  /**
   * This function is called with an SDK specific breadcrumb object before the breadcrumb is added
   * to the scope. When nothing is returned from the function, the breadcrumb is dropped
   */
  private @Nullable BeforeBreadcrumbCallback beforeBreadcrumb;

  /** The cache dir. path for caching offline events */
  private @Nullable String cacheDirPath;

  private int maxCacheItems = 30;

  /** Max. queue size before flushing events/envelopes to the disk */
  private int maxQueueSize = maxCacheItems;

  /**
   * This variable controls the total amount of breadcrumbs that should be captured Default is 100
   */
  private int maxBreadcrumbs = 100;

  /** Sets the release. SDK will try to automatically configure a release out of the box */
  private @Nullable String release;

  /**
   * Sets the environment. This string is freeform and not set by default. A release can be
   * associated with more than one environment to separate them in the UI Think staging vs prod or
   * similar.
   */
  private @Nullable String environment;

  /**
   * When set, a proxy can be configured that should be used for outbound requests. This is also
   * used for HTTPS requests
   */
  private @Nullable Proxy proxy;

  /**
   * Configures the sample rate as a percentage of events to be sent in the range of 0.0 to 1.0. if
   * 1.0 is set it means that 100% of events are sent. If set to 0.1 only 10% of events will be
   * sent. Events are picked randomly. Default is null (disabled)
   */
  private @Nullable Double sampleRate;

  /** Enables generation of transactions and propagation of trace data. */
  private @Nullable Boolean enableTracing;

  /**
   * Configures the sample rate as a percentage of transactions to be sent in the range of 0.0 to
   * 1.0. if 1.0 is set it means that 100% of transactions are sent. If set to 0.1 only 10% of
   * transactions will be sent. Transactions are picked randomly. Default is null (disabled)
   */
  private @Nullable Double tracesSampleRate;

  /**
   * This function is called by {@link TracesSampler} to determine if transaction is sampled - meant
   * to be sent to Sentry.
   */
  private @Nullable TracesSamplerCallback tracesSampler;

  /**
   * A list of string prefixes of module names that do not belong to the app, but rather third-party
   * packages. Modules considered not to be part of the app will be hidden from stack traces by
   * default.
   */
  private final @NotNull List<String> inAppExcludes = new CopyOnWriteArrayList<>();

  /**
   * A list of string prefixes of module names that belong to the app. This option takes precedence
   * over inAppExcludes.
   */
  private final @NotNull List<String> inAppIncludes = new CopyOnWriteArrayList<>();

  /**
   * The transport factory creates instances of {@link ITransport} - internal construct of the
   * client that abstracts away the event sending.
   */
  private @NotNull ITransportFactory transportFactory = NoOpTransportFactory.getInstance();

  /**
   * Implementations of this interface serve as gatekeepers that allow or disallow sending of the
   * events
   */
  private @NotNull ITransportGate transportGate = NoOpTransportGate.getInstance();

  /** Sets the distribution. Think about it together with release and environment */
  private @Nullable String dist;

  /** When enabled, all the threads are automatically attached to all logged events. */
  private boolean attachThreads;

  /**
   * When enabled, stack traces are automatically attached to all threads logged. Stack traces are
   * always attached to exceptions but when this is set stack traces are also sent with threads. If
   * no threads are logged, we log the current thread automatically.
   */
  private boolean attachStacktrace = true;

  /** Whether to enable or disable automatic session tracking. */
  private boolean enableAutoSessionTracking = true;

  /**
   * The session tracking interval in millis. This is the interval to end a session if the App goes
   * to the background.
   */
  private long sessionTrackingIntervalMillis = 30000; // 30s

  /** The distinct Id (generated Guid) used for session tracking */
  private @Nullable String distinctId;

  /** The server name used in the Sentry messages. */
  private @Nullable String serverName;

  /** Automatically resolve server name. */
  private boolean attachServerName = true;

  /** When enabled, Sentry installs UncaughtExceptionHandlerIntegration. */
  private boolean enableUncaughtExceptionHandler = true;

  /*
   * When enabled, UncaughtExceptionHandler will print exceptions (same as java would normally do),
   * if no other UncaughtExceptionHandler was registered before.
   */
  private boolean printUncaughtStackTrace = false;

  /** Sentry Executor Service that sends cached events and envelopes on App. start. */
  private @NotNull ISentryExecutorService executorService = NoOpSentryExecutorService.getInstance();

  /** connection timeout in milliseconds. */
  private int connectionTimeoutMillis = 5000;

  /** read timeout in milliseconds */
  private int readTimeoutMillis = 5000;

  /** Reads and caches envelope files in the disk */
  private @NotNull IEnvelopeCache envelopeDiskCache = NoOpEnvelopeCache.getInstance();

  /** SdkVersion object that contains the Sentry Client Name and its version */
  private @Nullable SdkVersion sdkVersion;

  /** whether to send personal identifiable information along with events */
  private boolean sendDefaultPii = false;

  /** SSLSocketFactory for self-signed certificate trust * */
  private @Nullable SSLSocketFactory sslSocketFactory;

  /** list of scope observers */
  private final @NotNull List<IScopeObserver> observers = new CopyOnWriteArrayList<>();

  private final @NotNull List<IOptionsObserver> optionsObservers = new CopyOnWriteArrayList<>();

  /**
   * Enables loading additional options from external locations like {@code sentry.properties} file
   * or environment variables, system properties.
   */
  private boolean enableExternalConfiguration;

  /** Tags applied to every event and transaction */
  private final @NotNull Map<String, @NotNull String> tags = new ConcurrentHashMap<>();

  /** max attachment size in bytes. */
  private long maxAttachmentSize = 20 * 1024 * 1024;

  /**
   * Enables event deduplication with {@link DuplicateEventDetectionEventProcessor}. Event
   * deduplication prevents from receiving the same exception multiple times when there is more than
   * one framework active that captures errors, for example Logback and Spring Boot.
   */
  private boolean enableDeduplication = true;

  /** Maximum number of spans that can be atteched to single transaction. */
  private int maxSpans = 1000;

  /** Registers hook that flushes {@link Hub} when main thread shuts down. */
  private boolean enableShutdownHook = true;

  /**
   * Controls the size of the request body to extract if any. No truncation is done by the SDK. If
   * the request body is larger than the accepted size, nothing is sent.
   */
  private @NotNull RequestSize maxRequestBodySize = RequestSize.NONE;

  /**
   * Controls if the `baggage` header is attached to HTTP client integrations and if the `trace`
   * header is attached to envelopes.
   */
  private boolean traceSampling = true;

  /**
   * Configures the profiles sample rate as a percentage of sampled transactions to be sent in the
   * range of 0.0 to 1.0. if 1.0 is set it means that 100% of sampled transactions will send a
   * profile. If set to 0.1 only 10% of sampled transactions will send a profile. Profiles are
   * picked randomly. Default is null (disabled)
   */
  private @Nullable Double profilesSampleRate;

  /**
   * This function is called by {@link TracesSampler} to determine if a profile is sampled - meant
   * to be sent to Sentry.
   */
  private @Nullable ProfilesSamplerCallback profilesSampler;

  /** Max trace file size in bytes. */
  private long maxTraceFileSize = 5 * 1024 * 1024;

  /** Listener interface to perform operations when a transaction is started or ended */
  private @NotNull ITransactionProfiler transactionProfiler = NoOpTransactionProfiler.getInstance();

  /**
   * Contains a list of origins to which `sentry-trace` header should be sent in HTTP integrations.
   */
  private @Nullable List<String> tracePropagationTargets = null;

  private final @NotNull List<String> defaultTracePropagationTargets =
      Collections.singletonList(DEFAULT_PROPAGATION_TARGETS);

  /** Proguard UUID. */
  private @Nullable String proguardUuid;

  /**
   * The idle time, measured in ms, to wait until the transaction will be finished. The transaction
   * will use the end timestamp of the last finished span as the endtime for the transaction.
   *
   * <p>When set to {@code null} the transaction must be finished manually.
   *
   * <p>The default is 3 seconds.
   */
  private @Nullable Long idleTimeout = 3000L;

  /**
   * Contains a list of context tags names (for example from MDC) that are meant to be applied as
   * Sentry tags to events.
   */
  private final @NotNull List<String> contextTags = new CopyOnWriteArrayList<>();

  /** Whether to send client reports containing information about number of dropped events. */
  private boolean sendClientReports = true;

  /** ClientReportRecorder to track count of lost events / transactions / ... * */
  @NotNull IClientReportRecorder clientReportRecorder = new ClientReportRecorder(this);

  /** Modules (dependencies, packages) that will be send along with each event. */
  private @NotNull IModulesLoader modulesLoader = NoOpModulesLoader.getInstance();

  /** Loads sentry-debug-meta.properties containing ProGuard UUID, bundle IDs etc. */
  private @NotNull IDebugMetaLoader debugMetaLoader = NoOpDebugMetaLoader.getInstance();

  /** Enables the Auto instrumentation for user interaction tracing. */
  private boolean enableUserInteractionTracing = false;

  /** Enable or disable automatic breadcrumbs for User interactions */
  private boolean enableUserInteractionBreadcrumbs = true;

  /** Which framework is responsible for instrumenting. */
  private @NotNull Instrumenter instrumenter = Instrumenter.SENTRY;

  /** Contains a list of GestureTargetLocator instances used for user interaction tracking */
  private final @NotNull List<GestureTargetLocator> gestureTargetLocators = new ArrayList<>();

  /**
   * Contains a list of ViewHierarchyExporter instances used for extracting non Android system View
   * Hierarchy elements
   */
  private final @NotNull List<ViewHierarchyExporter> viewHierarchyExporters = new ArrayList<>();

  private @NotNull IMainThreadChecker mainThreadChecker = NoOpMainThreadChecker.getInstance();

  // TODO this should default to false on the next major
  /** Whether OPTIONS requests should be traced. */
  private boolean traceOptionsRequests = true;

  /** Date provider to retrieve the current date from. */
  @ApiStatus.Internal
  private @NotNull SentryDateProvider dateProvider = new SentryAutoDateProvider();

  private final @NotNull List<IPerformanceCollector> performanceCollectors = new ArrayList<>();

  /** Performance collector that collect performance stats while transactions run. */
  private @NotNull TransactionPerformanceCollector transactionPerformanceCollector =
      NoOpTransactionPerformanceCollector.getInstance();

  /** Enables the time-to-full-display spans in navigation transactions. */
  private boolean enableTimeToFullDisplayTracing = false;

  /** Screen fully displayed reporter, used for time-to-full-display spans. */
  private final @NotNull FullyDisplayedReporter fullyDisplayedReporter =
      FullyDisplayedReporter.getInstance();

  private @NotNull IConnectionStatusProvider connectionStatusProvider =
      new NoOpConnectionStatusProvider();

  /** Whether Sentry should be enabled */
  private boolean enabled = true;

  /** Whether to format serialized data, e.g. events logged to console in debug mode */
  private boolean enablePrettySerializationOutput = true;

  /** Whether to send modules containing information about versions. */
  private boolean sendModules = true;

  private @Nullable BeforeEnvelopeCallback beforeEnvelopeCallback;

  private boolean enableSpotlight = false;

  private @Nullable String spotlightConnectionUrl;

  /** Whether to enable scope persistence so the scope values are preserved if the process dies */
  private boolean enableScopePersistence = true;

  /** Contains a list of monitor slugs for which check-ins should not be sent. */
  @ApiStatus.Experimental private @Nullable List<String> ignoredCheckIns = null;

  @ApiStatus.Experimental
  private @NotNull IBackpressureMonitor backpressureMonitor = NoOpBackpressureMonitor.getInstance();

  @ApiStatus.Experimental private boolean enableBackpressureHandling = false;

  /** Whether to profile app launches, depending on profilesSampler or profilesSampleRate. */
  private boolean enableAppStartProfiling = false;

  private boolean enableMetrics = false;

  /**
   * Profiling traces rate. 101 hz means 101 traces in 1 second. Defaults to 101 to avoid possible
   * lockstep sampling. More on
   * https://stackoverflow.com/questions/45470758/what-is-lockstep-sampling
   */
  private int profilingTracesHz = 101;

  /**
   * Adds an event processor
   *
   * @param eventProcessor the event processor
   */
  public void addEventProcessor(@NotNull EventProcessor eventProcessor) {
    eventProcessors.add(eventProcessor);
  }

  /**
   * Returns the list of event processors
   *
   * @return the event processor list
   */
  public @NotNull List<EventProcessor> getEventProcessors() {
    return eventProcessors;
  }

  /**
   * Adds an integration
   *
   * @param integration the integration
   */
  public void addIntegration(@NotNull Integration integration) {
    integrations.add(integration);
  }

  /**
   * Returns the list of integrations
   *
   * @return the integration list
   */
  public @NotNull List<Integration> getIntegrations() {
    return integrations;
  }

  /**
   * Returns the DSN
   *
   * @return the DSN or null if not set
   */
  public @Nullable String getDsn() {
    return dsn;
  }

  /**
   * Sets the DSN
   *
   * @param dsn the DSN
   */
  public void setDsn(final @Nullable String dsn) {
    this.dsn = dsn;

    dsnHash = StringUtils.calculateStringHash(this.dsn, logger);
  }

  /**
   * Check if debug mode is ON Default is OFF
   *
   * @return true if ON or false otherwise
   */
  public boolean isDebug() {
    return debug;
  }

  /**
   * Sets the debug mode to ON or OFF Default is OFF
   *
   * @param debug true if ON or false otherwise
   */
  public void setDebug(final boolean debug) {
    this.debug = debug;
  }

  /**
   * Returns the Logger interface
   *
   * @return the logger
   */
  public @NotNull ILogger getLogger() {
    return logger;
  }

  /**
   * Sets the Logger interface if null, logger will be NoOp
   *
   * @param logger the logger interface
   */
  public void setLogger(final @Nullable ILogger logger) {
    this.logger = (logger == null) ? NoOpLogger.getInstance() : new DiagnosticLogger(this, logger);
  }

  /**
   * Returns the minimum LogLevel
   *
   * @return the log level
   */
  public @NotNull SentryLevel getDiagnosticLevel() {
    return diagnosticLevel;
  }

  /**
   * Sets the minimum LogLevel if null, it uses the default min. LogLevel Default is DEBUG
   *
   * @param diagnosticLevel the log level
   */
  public void setDiagnosticLevel(@Nullable final SentryLevel diagnosticLevel) {
    this.diagnosticLevel = (diagnosticLevel != null) ? diagnosticLevel : DEFAULT_DIAGNOSTIC_LEVEL;
  }

  /**
   * Returns the Serializer interface
   *
   * @return the serializer
   */
  public @NotNull ISerializer getSerializer() {
    return serializer;
  }

  /**
   * Sets the Serializer interface if null, Serializer will be NoOp
   *
   * @param serializer the serializer
   */
  public void setSerializer(@Nullable ISerializer serializer) {
    this.serializer = serializer != null ? serializer : NoOpSerializer.getInstance();
  }

  /**
   * Returns the max depth for when serializing object graphs using reflection.
   *
   * @return the max depth
   */
  public int getMaxDepth() {
    return maxDepth;
  }

  /**
   * Set the max depth for when serializing object graphs using reflection.
   *
   * @param maxDepth the max depth
   */
  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public @NotNull IEnvelopeReader getEnvelopeReader() {
    return envelopeReader;
  }

  public void setEnvelopeReader(final @Nullable IEnvelopeReader envelopeReader) {
    this.envelopeReader =
        envelopeReader != null ? envelopeReader : NoOpEnvelopeReader.getInstance();
  }

  /**
   * Returns the shutdown timeout in Millis
   *
   * @deprecated use {{@link SentryOptions#getShutdownTimeoutMillis()} }
   * @return the timeout in Millis
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public long getShutdownTimeout() {
    return shutdownTimeoutMillis;
  }

  /**
   * Returns the shutdown timeout in Millis
   *
   * @return the timeout in Millis
   */
  public long getShutdownTimeoutMillis() {
    return shutdownTimeoutMillis;
  }

  /**
   * Sets the shutdown timeout in Millis Default is 2000 = 2s
   *
   * @deprecated use {{@link SentryOptions#setShutdownTimeoutMillis(long)} }
   * @param shutdownTimeoutMillis the shutdown timeout in millis
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public void setShutdownTimeout(long shutdownTimeoutMillis) {
    this.shutdownTimeoutMillis = shutdownTimeoutMillis;
  }

  /**
   * Sets the shutdown timeout in Millis Default is 2000 = 2s
   *
   * @param shutdownTimeoutMillis the shutdown timeout in millis
   */
  public void setShutdownTimeoutMillis(long shutdownTimeoutMillis) {
    this.shutdownTimeoutMillis = shutdownTimeoutMillis;
  }

  /**
   * Returns the Sentry client name
   *
   * @return the Sentry client name or null if not set
   */
  public @Nullable String getSentryClientName() {
    return sentryClientName;
  }

  /**
   * Sets the Sentry client name
   *
   * @param sentryClientName the Sentry client name
   */
  public void setSentryClientName(@Nullable String sentryClientName) {
    this.sentryClientName = sentryClientName;
  }

  /**
   * Returns the BeforeSend callback
   *
   * @return the beforeSend callback or null if not set
   */
  public @Nullable BeforeSendCallback getBeforeSend() {
    return beforeSend;
  }

  /**
   * Sets the beforeSend callback
   *
   * @param beforeSend the beforeSend callback
   */
  public void setBeforeSend(@Nullable BeforeSendCallback beforeSend) {
    this.beforeSend = beforeSend;
  }

  /**
   * Returns the BeforeSendTransaction callback
   *
   * @return the beforeSendTransaction callback or null if not set
   */
  public @Nullable BeforeSendTransactionCallback getBeforeSendTransaction() {
    return beforeSendTransaction;
  }

  /**
   * Sets the beforeSendTransaction callback
   *
   * @param beforeSendTransaction the beforeSendTransaction callback
   */
  public void setBeforeSendTransaction(
      @Nullable BeforeSendTransactionCallback beforeSendTransaction) {
    this.beforeSendTransaction = beforeSendTransaction;
  }

  /**
   * Returns the beforeBreadcrumb callback
   *
   * @return the beforeBreadcrumb callback or null if not set
   */
  public @Nullable BeforeBreadcrumbCallback getBeforeBreadcrumb() {
    return beforeBreadcrumb;
  }

  /**
   * Sets the beforeBreadcrumb callback
   *
   * @param beforeBreadcrumb the beforeBreadcrumb callback
   */
  public void setBeforeBreadcrumb(@Nullable BeforeBreadcrumbCallback beforeBreadcrumb) {
    this.beforeBreadcrumb = beforeBreadcrumb;
  }

  /**
   * Returns the cache dir. path if set
   *
   * @return the cache dir. path or null if not set
   */
  public @Nullable String getCacheDirPath() {
    if (cacheDirPath == null || cacheDirPath.isEmpty()) {
      return null;
    }

    return dsnHash != null ? new File(cacheDirPath, dsnHash).getAbsolutePath() : cacheDirPath;
  }

  /**
   * Returns the cache dir path if set, without the appended dsn hash.
   *
   * @return the cache dir path, without the appended dsn hash, or null if not set.
   */
  @Nullable
  String getCacheDirPathWithoutDsn() {
    if (cacheDirPath == null || cacheDirPath.isEmpty()) {
      return null;
    }

    return cacheDirPath;
  }

  /**
   * Returns the outbox path if cacheDirPath is set
   *
   * @return the outbox path or null if not set
   */
  public @Nullable String getOutboxPath() {
    final String cacheDirPath = getCacheDirPath();
    if (cacheDirPath == null) {
      return null;
    }
    return new File(cacheDirPath, "outbox").getAbsolutePath();
  }

  /**
   * Sets the cache dir. path
   *
   * @param cacheDirPath the cache dir. path
   */
  public void setCacheDirPath(final @Nullable String cacheDirPath) {
    this.cacheDirPath = cacheDirPath;
  }

  /**
   * Returns the max Breadcrumbs Default is 100
   *
   * @return the max breadcrumbs
   */
  public int getMaxBreadcrumbs() {
    return maxBreadcrumbs;
  }

  /**
   * Sets the max breadcrumbs Default is 100
   *
   * @param maxBreadcrumbs the max breadcrumbs
   */
  public void setMaxBreadcrumbs(int maxBreadcrumbs) {
    this.maxBreadcrumbs = maxBreadcrumbs;
  }

  /**
   * Returns the release
   *
   * @return the release or null if not set
   */
  public @Nullable String getRelease() {
    return release;
  }

  /**
   * Sets the release
   *
   * @param release the release
   */
  public void setRelease(@Nullable String release) {
    this.release = release;
  }

  /**
   * Returns the environment
   *
   * @return the environment or 'production' if not set
   */
  public @Nullable String getEnvironment() {
    return environment != null ? environment : DEFAULT_ENVIRONMENT;
  }

  /**
   * Sets the environment
   *
   * @param environment the environment
   */
  public void setEnvironment(@Nullable String environment) {
    this.environment = environment;
  }

  /**
   * Returns the proxy if set
   *
   * @return the proxy or null if not set
   */
  public @Nullable Proxy getProxy() {
    return proxy;
  }

  /**
   * Sets the proxy
   *
   * @param proxy the proxy
   */
  public void setProxy(@Nullable Proxy proxy) {
    this.proxy = proxy;
  }

  /**
   * Returns the sample rate Default is null (disabled)
   *
   * @return the sample rate
   */
  public @Nullable Double getSampleRate() {
    return sampleRate;
  }

  /**
   * Sets the sampleRate Can be anything between 0.0 and 1.0 or null (default), to disable it.
   *
   * @param sampleRate the sample rate
   */
  public void setSampleRate(Double sampleRate) {
    if (!SampleRateUtils.isValidSampleRate(sampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + sampleRate
              + " is not valid. Use null to disable or values >= 0.0 and <= 1.0.");
    }
    this.sampleRate = sampleRate;
  }

  /**
   * Whether generation of transactions and propagation of trace data is enabled.
   *
   * <p>NOTE: There is also {@link SentryOptions#isTracingEnabled()} which checks other options as
   * well.
   *
   * @return true if enabled, false if disabled, null can mean enabled if {@link
   *     SentryOptions#getTracesSampleRate()} or {@link SentryOptions#getTracesSampler()} are set.
   */
  public @Nullable Boolean getEnableTracing() {
    return enableTracing;
  }

  /** Enables generation of transactions and propagation of trace data. */
  public void setEnableTracing(@Nullable Boolean enableTracing) {
    this.enableTracing = enableTracing;
  }

  /**
   * Returns the traces sample rate Default is null (disabled)
   *
   * @return the sample rate
   */
  public @Nullable Double getTracesSampleRate() {
    return tracesSampleRate;
  }

  /**
   * Sets the tracesSampleRate Can be anything between 0.0 and 1.0 or null (default), to disable it.
   *
   * @param tracesSampleRate the sample rate
   */
  public void setTracesSampleRate(final @Nullable Double tracesSampleRate) {
    if (!SampleRateUtils.isValidTracesSampleRate(tracesSampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + tracesSampleRate
              + " is not valid. Use null to disable or values between 0.0 and 1.0.");
    }
    this.tracesSampleRate = tracesSampleRate;
  }

  /**
   * Returns the callback used to determine if transaction is sampled.
   *
   * @return the callback
   */
  public @Nullable TracesSamplerCallback getTracesSampler() {
    return tracesSampler;
  }

  /**
   * Sets the callback used to determine if transaction is sampled.
   *
   * @param tracesSampler the callback
   */
  public void setTracesSampler(final @Nullable TracesSamplerCallback tracesSampler) {
    this.tracesSampler = tracesSampler;
  }

  /**
   * the list of inApp excludes
   *
   * @return the inApp excludes list
   */
  public @NotNull List<String> getInAppExcludes() {
    return inAppExcludes;
  }

  /**
   * Adds an inApp exclude
   *
   * @param exclude the inApp exclude module/package
   */
  public void addInAppExclude(@NotNull String exclude) {
    inAppExcludes.add(exclude);
  }

  /**
   * Returns the inApp includes list
   *
   * @return the inApp includes list
   */
  public @NotNull List<String> getInAppIncludes() {
    return inAppIncludes;
  }

  /**
   * Adds an inApp include
   *
   * @param include the inApp include module/package
   */
  public void addInAppInclude(@NotNull String include) {
    inAppIncludes.add(include);
  }

  /**
   * Returns the TransportFactory interface
   *
   * @return the transport factory
   */
  public @NotNull ITransportFactory getTransportFactory() {
    return transportFactory;
  }

  /**
   * Sets the TransportFactory interface
   *
   * @param transportFactory the transport factory
   */
  public void setTransportFactory(@Nullable ITransportFactory transportFactory) {
    this.transportFactory =
        transportFactory != null ? transportFactory : NoOpTransportFactory.getInstance();
  }

  /**
   * Sets the distribution
   *
   * @return the distribution or null if not set
   */
  public @Nullable String getDist() {
    return dist;
  }

  /**
   * Sets the distribution
   *
   * @param dist the distribution
   */
  public void setDist(@Nullable String dist) {
    this.dist = dist;
  }

  /**
   * Returns the TransportGate interface
   *
   * @return the transport gate
   */
  public @NotNull ITransportGate getTransportGate() {
    return transportGate;
  }

  /**
   * Sets the TransportGate interface
   *
   * @param transportGate the transport gate
   */
  public void setTransportGate(@Nullable ITransportGate transportGate) {
    this.transportGate = (transportGate != null) ? transportGate : NoOpTransportGate.getInstance();
  }

  /**
   * Checks if the AttachStacktrace is enabled or not
   *
   * @return true if enabled or false otherwise
   */
  public boolean isAttachStacktrace() {
    return attachStacktrace;
  }

  /**
   * Sets the attachStacktrace to enabled or disabled
   *
   * @param attachStacktrace true if enabled or false otherwise
   */
  public void setAttachStacktrace(boolean attachStacktrace) {
    this.attachStacktrace = attachStacktrace;
  }

  /**
   * Checks if the AttachThreads is enabled or not
   *
   * @return true if enabled or false otherwise
   */
  public boolean isAttachThreads() {
    return attachThreads;
  }

  /**
   * Sets the attachThreads to enabled or disabled
   *
   * @param attachThreads true if enabled or false otherwise
   */
  public void setAttachThreads(boolean attachThreads) {
    this.attachThreads = attachThreads;
  }

  /**
   * Returns if the automatic session tracking is enabled or not
   *
   * @return true if enabled or false otherwise
   */
  public boolean isEnableAutoSessionTracking() {
    return enableAutoSessionTracking;
  }

  /**
   * Enable or disable the automatic session tracking
   *
   * @param enableAutoSessionTracking true if enabled or false otherwise
   */
  public void setEnableAutoSessionTracking(final boolean enableAutoSessionTracking) {
    this.enableAutoSessionTracking = enableAutoSessionTracking;
  }

  /**
   * Gets the default server name to be used in Sentry events.
   *
   * @return the default server name or null if none set
   */
  public @Nullable String getServerName() {
    return serverName;
  }

  /**
   * Sets the default server name to be used in Sentry events.
   *
   * @param serverName the default server name or null if none should be used
   */
  public void setServerName(@Nullable String serverName) {
    this.serverName = serverName;
  }

  /**
   * Returns if SDK automatically resolves and attaches server name to events.
   *
   * @return true if enabled false if otherwise
   */
  public boolean isAttachServerName() {
    return attachServerName;
  }

  /**
   * Sets if SDK should automatically resolve and attache server name to events.
   *
   * @param attachServerName true if enabled false if otherwise
   */
  public void setAttachServerName(boolean attachServerName) {
    this.attachServerName = attachServerName;
  }

  /**
   * Returns the session tracking interval in millis
   *
   * @return the interval in millis
   */
  public long getSessionTrackingIntervalMillis() {
    return sessionTrackingIntervalMillis;
  }

  /**
   * Sets the session tracking interval in millis
   *
   * @param sessionTrackingIntervalMillis the interval in millis
   */
  public void setSessionTrackingIntervalMillis(long sessionTrackingIntervalMillis) {
    this.sessionTrackingIntervalMillis = sessionTrackingIntervalMillis;
  }

  /**
   * Returns the distinct Id
   *
   * @return the distinct Id
   */
  @ApiStatus.Internal
  public @Nullable String getDistinctId() {
    return distinctId;
  }

  /**
   * Sets the distinct Id
   *
   * @param distinctId the distinct Id
   */
  @ApiStatus.Internal
  public void setDistinctId(final @Nullable String distinctId) {
    this.distinctId = distinctId;
  }

  /**
   * Returns the flush timeout in millis
   *
   * @return the timeout in millis
   */
  public long getFlushTimeoutMillis() {
    return flushTimeoutMillis;
  }

  /**
   * Sets the flush timeout in millis
   *
   * @param flushTimeoutMillis the timeout in millis
   */
  public void setFlushTimeoutMillis(long flushTimeoutMillis) {
    this.flushTimeoutMillis = flushTimeoutMillis;
  }

  /**
   * Checks if the default UncaughtExceptionHandlerIntegration is enabled or not.
   *
   * @return true if enabled or false otherwise.
   */
  public boolean isEnableUncaughtExceptionHandler() {
    return enableUncaughtExceptionHandler;
  }

  /**
   * Enable or disable the default UncaughtExceptionHandlerIntegration.
   *
   * @param enableUncaughtExceptionHandler true if enabled or false otherwise.
   */
  public void setEnableUncaughtExceptionHandler(final boolean enableUncaughtExceptionHandler) {
    this.enableUncaughtExceptionHandler = enableUncaughtExceptionHandler;
  }

  /**
   * Checks if printing exceptions by UncaughtExceptionHandler is enabled or disabled.
   *
   * @return true if enabled or false otherwise.
   */
  public boolean isPrintUncaughtStackTrace() {
    return printUncaughtStackTrace;
  }

  /**
   * Enable or disable printing exceptions in UncaughtExceptionHandler
   *
   * @param printUncaughtStackTrace true if enabled or false otherwise.
   */
  public void setPrintUncaughtStackTrace(final boolean printUncaughtStackTrace) {
    this.printUncaughtStackTrace = printUncaughtStackTrace;
  }

  /**
   * Returns the SentryExecutorService
   *
   * @return the SentryExecutorService
   */
  @ApiStatus.Internal
  @NotNull
  public ISentryExecutorService getExecutorService() {
    return executorService;
  }

  /**
   * Sets the SentryExecutorService
   *
   * @param executorService the SentryExecutorService
   */
  @ApiStatus.Internal
  @TestOnly
  public void setExecutorService(final @NotNull ISentryExecutorService executorService) {
    if (executorService != null) {
      this.executorService = executorService;
    }
  }

  /**
   * Returns the connection timeout in milliseconds.
   *
   * @return the connectionTimeoutMillis
   */
  public int getConnectionTimeoutMillis() {
    return connectionTimeoutMillis;
  }

  /**
   * Sets the connection timeout in milliseconds.
   *
   * @param connectionTimeoutMillis the connectionTimeoutMillis
   */
  public void setConnectionTimeoutMillis(int connectionTimeoutMillis) {
    this.connectionTimeoutMillis = connectionTimeoutMillis;
  }

  /**
   * Returns the read timeout in milliseconds
   *
   * @return the readTimeoutMillis
   */
  public int getReadTimeoutMillis() {
    return readTimeoutMillis;
  }

  /**
   * Sets the read timeout in milliseconds
   *
   * @param readTimeoutMillis the readTimeoutMillis
   */
  public void setReadTimeoutMillis(int readTimeoutMillis) {
    this.readTimeoutMillis = readTimeoutMillis;
  }

  /**
   * Returns the EnvelopeCache interface
   *
   * @return the EnvelopeCache object
   */
  public @NotNull IEnvelopeCache getEnvelopeDiskCache() {
    return envelopeDiskCache;
  }

  /**
   * Sets the EnvelopeCache interface
   *
   * @param envelopeDiskCache the EnvelopeCache object
   */
  public void setEnvelopeDiskCache(final @Nullable IEnvelopeCache envelopeDiskCache) {
    this.envelopeDiskCache =
        envelopeDiskCache != null ? envelopeDiskCache : NoOpEnvelopeCache.getInstance();
  }

  /**
   * Returns the Max queue size
   *
   * @return the max queue size
   */
  public int getMaxQueueSize() {
    return maxQueueSize;
  }

  /**
   * Sets the max queue size if maxQueueSize is bigger than 0
   *
   * @param maxQueueSize max queue size
   */
  public void setMaxQueueSize(int maxQueueSize) {
    if (maxQueueSize > 0) {
      this.maxQueueSize = maxQueueSize;
    }
  }

  /**
   * Returns the SdkVersion object
   *
   * @return the SdkVersion object or null
   */
  public @Nullable SdkVersion getSdkVersion() {
    return sdkVersion;
  }

  /**
   * Returns SSLSocketFactory
   *
   * @return SSLSocketFactory object or null
   */
  public @Nullable SSLSocketFactory getSslSocketFactory() {
    return sslSocketFactory;
  }

  /**
   * Set custom SSLSocketFactory that is trusted to self-signed certificates
   *
   * @param sslSocketFactory SSLSocketFactory object
   */
  public void setSslSocketFactory(final @Nullable SSLSocketFactory sslSocketFactory) {
    this.sslSocketFactory = sslSocketFactory;
  }

  /**
   * Sets the SdkVersion object
   *
   * @param sdkVersion the SdkVersion object or null
   */
  @ApiStatus.Internal
  public void setSdkVersion(final @Nullable SdkVersion sdkVersion) {
    this.sdkVersion = sdkVersion;
  }

  public boolean isSendDefaultPii() {
    return sendDefaultPii;
  }

  public void setSendDefaultPii(boolean sendDefaultPii) {
    this.sendDefaultPii = sendDefaultPii;
  }

  /**
   * Adds a Scope observer
   *
   * @param observer the Observer
   */
  public void addScopeObserver(final @NotNull IScopeObserver observer) {
    observers.add(observer);
  }

  /**
   * Returns the list of Scope observers
   *
   * @return the Scope observer list
   */
  @NotNull
  public List<IScopeObserver> getScopeObservers() {
    return observers;
  }

  /**
   * Adds a SentryOptions observer
   *
   * @param observer the Observer
   */
  public void addOptionsObserver(final @NotNull IOptionsObserver observer) {
    optionsObservers.add(observer);
  }

  /**
   * Returns the list of SentryOptions observers
   *
   * @return the SentryOptions observer list
   */
  @NotNull
  public List<IOptionsObserver> getOptionsObservers() {
    return optionsObservers;
  }

  /**
   * Returns if loading properties from external sources is enabled.
   *
   * @return true if enabled or false otherwise
   */
  public boolean isEnableExternalConfiguration() {
    return enableExternalConfiguration;
  }

  /**
   * Enables loading options from external sources like sentry.properties file or environment
   * variables, system properties.
   *
   * @param enableExternalConfiguration true if enabled or false otherwise
   */
  public void setEnableExternalConfiguration(boolean enableExternalConfiguration) {
    this.enableExternalConfiguration = enableExternalConfiguration;
  }

  /**
   * Returns tags applied to all events and transactions.
   *
   * @return the tags map
   */
  public @NotNull Map<String, String> getTags() {
    return tags;
  }

  /**
   * Sets a tag that is applied to all events and transactions.
   *
   * @param key the key
   * @param value the value
   */
  public void setTag(final @NotNull String key, final @NotNull String value) {
    this.tags.put(key, value);
  }

  /**
   * Returns the maximum attachment size for each attachment in MiB.
   *
   * @return the maximum attachment size in MiB.
   */
  public long getMaxAttachmentSize() {
    return maxAttachmentSize;
  }

  /**
   * Sets the max attachment size for each attachment in bytes. Default is 20 MiB. Please also check
   * the maximum attachment size of Relay to make sure your attachments don't get discarded there:
   * https://docs.sentry.io/product/relay/options/
   *
   * @param maxAttachmentSize the max attachment size in bytes.
   */
  public void setMaxAttachmentSize(long maxAttachmentSize) {
    this.maxAttachmentSize = maxAttachmentSize;
  }

  /**
   * Returns if event deduplication is turned on.
   *
   * @return if event deduplication is turned on.
   */
  public boolean isEnableDeduplication() {
    return enableDeduplication;
  }

  /**
   * Enables or disables event deduplication.
   *
   * @param enableDeduplication true if enabled false otherwise
   */
  public void setEnableDeduplication(final boolean enableDeduplication) {
    this.enableDeduplication = enableDeduplication;
  }

  /**
   * Returns if tracing should be enabled. If tracing is disabled, starting transactions returns
   * {@link NoOpTransaction}.
   *
   * @return if tracing is enabled.
   */
  public boolean isTracingEnabled() {
    if (enableTracing != null) {
      return enableTracing;
    }

    return getTracesSampleRate() != null || getTracesSampler() != null;
  }

  /**
   * Returns the list of exception classes that once captured will not be sent to Sentry as {@link
   * SentryEvent}.
   *
   * @return the list of exception classes that once captured will not be sent to Sentry as {@link
   *     SentryEvent}.
   */
  public @NotNull Set<Class<? extends Throwable>> getIgnoredExceptionsForType() {
    return ignoredExceptionsForType;
  }

  /**
   * Adds exception type to the list of ignored exceptions.
   *
   * @param exceptionType - the exception type
   */
  public void addIgnoredExceptionForType(final @NotNull Class<? extends Throwable> exceptionType) {
    this.ignoredExceptionsForType.add(exceptionType);
  }

  /**
   * Checks if the type of exception given by parameter is ignored.
   *
   * @param throwable the throwable
   * @return if the type of exception is ignored
   */
  boolean containsIgnoredExceptionForType(final @NotNull Throwable throwable) {
    return this.ignoredExceptionsForType.contains(throwable.getClass());
  }

  /**
   * Returns the maximum number of spans that can be attached to single transaction.
   *
   * @return the maximum number of spans that can be attached to single transaction.
   */
  @ApiStatus.Experimental
  public int getMaxSpans() {
    return maxSpans;
  }

  /**
   * Sets the maximum number of spans that can be attached to single transaction.
   *
   * @param maxSpans maximum number of spans that can be attached to single transaction.
   */
  @ApiStatus.Experimental
  public void setMaxSpans(int maxSpans) {
    this.maxSpans = maxSpans;
  }

  /**
   * True if ShutdownHookIntegration is enabled, false otherwise.
   *
   * @return true if enabled or false otherwise.
   */
  public boolean isEnableShutdownHook() {
    return enableShutdownHook;
  }

  /**
   * Enables or disable ShutdownHookIntegration.
   *
   * @param enableShutdownHook true if enabled or false otherwise.
   */
  public void setEnableShutdownHook(boolean enableShutdownHook) {
    this.enableShutdownHook = enableShutdownHook;
  }

  /**
   * The max cache items for capping the number of events Default is 30
   *
   * @return the maxCacheItems
   */
  public int getMaxCacheItems() {
    return maxCacheItems;
  }

  /**
   * Sets the max cache items for capping the number of events
   *
   * @param maxCacheItems the maxCacheItems
   */
  public void setMaxCacheItems(int maxCacheItems) {
    this.maxCacheItems = maxCacheItems;
  }

  public @NotNull RequestSize getMaxRequestBodySize() {
    return maxRequestBodySize;
  }

  public void setMaxRequestBodySize(final @NotNull RequestSize maxRequestBodySize) {
    this.maxRequestBodySize = maxRequestBodySize;
  }

  /**
   * Returns whether the `baggage` header is attached to HTTP client integrations and the `trace`
   * header is attached to envelopes.
   *
   * <p>Note: this is an experimental API and will be removed without notice.
   *
   * @return true if enabled
   */
  @ApiStatus.Experimental
  public boolean isTraceSampling() {
    return traceSampling;
  }

  /**
   * Controls if the `baggage` header is attached HTTP client integrations and if the `trace` header
   * is attached to envelopes. Defaults to false.
   *
   * <p>Note: this is an experimental API and will be removed without notice.
   *
   * @deprecated please use {{@link SentryOptions#setTracePropagationTargets(List)}} instead
   * @param traceSampling - if trace sampling should be enabled
   */
  @Deprecated
  public void setTraceSampling(boolean traceSampling) {
    this.traceSampling = traceSampling;
  }

  /**
   * Returns the maximum trace file size for each envelope item in bytes.
   *
   * @return the maximum attachment size in bytes.
   */
  public long getMaxTraceFileSize() {
    return maxTraceFileSize;
  }

  /**
   * Sets the max trace file size for each envelope item in bytes. Default is 5 Mb.
   *
   * @param maxTraceFileSize the max trace file size in bytes.
   */
  public void setMaxTraceFileSize(long maxTraceFileSize) {
    this.maxTraceFileSize = maxTraceFileSize;
  }

  /**
   * Returns the listener interface to perform operations when a transaction is started or ended.
   *
   * @return the listener interface to perform operations when a transaction is started or ended.
   */
  public @NotNull ITransactionProfiler getTransactionProfiler() {
    return transactionProfiler;
  }

  /**
   * Sets the listener interface to perform operations when a transaction is started or ended. It
   * only has effect if no profiler was already set.
   *
   * @param transactionProfiler - the listener for operations when a transaction is started or ended
   */
  public void setTransactionProfiler(final @Nullable ITransactionProfiler transactionProfiler) {
    // We allow to set the profiler only if it was not set before, and we don't allow to unset it.
    if (this.transactionProfiler == NoOpTransactionProfiler.getInstance()
        && transactionProfiler != null) {
      this.transactionProfiler = transactionProfiler;
    }
  }

  /**
   * Returns if profiling is enabled for transactions.
   *
   * @return if profiling is enabled for transactions.
   */
  public boolean isProfilingEnabled() {
    return (getProfilesSampleRate() != null && getProfilesSampleRate() > 0)
        || getProfilesSampler() != null;
  }

  /**
   * Sets whether profiling is enabled for transactions.
   *
   * @deprecated use {{@link SentryOptions#setProfilesSampleRate(Double)} }
   * @param profilingEnabled - whether profiling is enabled for transactions
   */
  @Deprecated
  public void setProfilingEnabled(boolean profilingEnabled) {
    if (getProfilesSampleRate() == null) {
      setProfilesSampleRate(profilingEnabled ? 1.0 : null);
    }
  }

  /**
   * Returns the callback used to determine if a profile is sampled.
   *
   * @return the callback
   */
  public @Nullable ProfilesSamplerCallback getProfilesSampler() {
    return profilesSampler;
  }

  /**
   * Sets the callback used to determine if a profile is sampled.
   *
   * @param profilesSampler the callback
   */
  public void setProfilesSampler(final @Nullable ProfilesSamplerCallback profilesSampler) {
    this.profilesSampler = profilesSampler;
  }

  /**
   * Returns the profiles sample rate. Default is null (disabled).
   *
   * @return the sample rate
   */
  public @Nullable Double getProfilesSampleRate() {
    return profilesSampleRate;
  }

  /**
   * Sets the profilesSampleRate. Can be anything between 0.0 and 1.0 or null (default), to disable
   * it. It's dependent on the {{@link SentryOptions#setTracesSampleRate(Double)} } If a transaction
   * is sampled, then a profile could be sampled with a probability given by profilesSampleRate.
   *
   * @param profilesSampleRate the sample rate
   */
  public void setProfilesSampleRate(final @Nullable Double profilesSampleRate) {
    if (!SampleRateUtils.isValidProfilesSampleRate(profilesSampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + profilesSampleRate
              + " is not valid. Use null to disable or values between 0.0 and 1.0.");
    }
    this.profilesSampleRate = profilesSampleRate;
  }

  /**
   * Returns the profiling traces dir. path if set
   *
   * @return the profiling traces dir. path or null if not set
   */
  public @Nullable String getProfilingTracesDirPath() {
    final String cacheDirPath = getCacheDirPath();
    if (cacheDirPath == null) {
      return null;
    }
    return new File(cacheDirPath, "profiling_traces").getAbsolutePath();
  }

  /**
   * Returns a list of origins to which `sentry-trace` header should be sent in HTTP integrations.
   *
   * @deprecated use {{@link SentryOptions#getTracePropagationTargets()} }
   * @return the list of origins
   */
  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public @NotNull List<String> getTracingOrigins() {
    return getTracePropagationTargets();
  }

  /**
   * Adds an origin to which `sentry-trace` header should be sent in HTTP integrations.
   *
   * @deprecated use {{@link SentryOptions#setTracePropagationTargets(List)}}
   * @param tracingOrigin - the tracing origin
   */
  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public void addTracingOrigin(final @NotNull String tracingOrigin) {
    if (tracePropagationTargets == null) {
      tracePropagationTargets = new CopyOnWriteArrayList<>();
    }
    if (!tracingOrigin.isEmpty()) {
      tracePropagationTargets.add(tracingOrigin);
    }
  }

  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  @ApiStatus.Internal
  public void setTracingOrigins(final @Nullable List<String> tracingOrigins) {
    setTracePropagationTargets(tracingOrigins);
  }

  /**
   * Returns a list of origins to which `sentry-trace` header should be sent in HTTP integrations.
   *
   * @return the list of targets
   */
  public @NotNull List<String> getTracePropagationTargets() {
    if (tracePropagationTargets == null) {
      return defaultTracePropagationTargets;
    }
    return tracePropagationTargets;
  }

  @ApiStatus.Internal
  public void setTracePropagationTargets(final @Nullable List<String> tracePropagationTargets) {
    if (tracePropagationTargets == null) {
      this.tracePropagationTargets = null;
    } else {
      @NotNull final List<String> filteredTracePropagationTargets = new ArrayList<>();
      for (String target : tracePropagationTargets) {
        if (!target.isEmpty()) {
          filteredTracePropagationTargets.add(target);
        }
      }

      this.tracePropagationTargets = filteredTracePropagationTargets;
    }
  }

  /**
   * Returns a Proguard UUID.
   *
   * @return the Proguard UUIDs.
   */
  public @Nullable String getProguardUuid() {
    return proguardUuid;
  }

  /**
   * Sets a Proguard UUID.
   *
   * @param proguardUuid - the Proguard UUID
   */
  public void setProguardUuid(final @Nullable String proguardUuid) {
    this.proguardUuid = proguardUuid;
  }

  /**
   * Adds a bundle ID (also known as debugId) representing a source bundle that contains sources for
   * this application. These sources will be used to source code for frames of an exceptions stack
   * trace.
   *
   * @param bundleId Bundle ID generated by sentry-cli or the sentry-android-gradle-plugin
   */
  public void addBundleId(final @Nullable String bundleId) {
    if (bundleId != null) {
      final @NotNull String trimmedBundleId = bundleId.trim();
      if (!trimmedBundleId.isEmpty()) {
        this.bundleIds.add(trimmedBundleId);
      }
    }
  }

  /**
   * Returns all configured bundle IDs referencing source code bundles.
   *
   * @return list of bundle IDs
   */
  public @NotNull Set<String> getBundleIds() {
    return bundleIds;
  }

  /**
   * Returns Context tags names applied to Sentry events as Sentry tags.
   *
   * @return context tags
   */
  public @NotNull List<String> getContextTags() {
    return contextTags;
  }

  /**
   * Adds context tag name that is applied to Sentry events as Sentry tag.
   *
   * @param contextTag - the context tag
   */
  public void addContextTag(final @NotNull String contextTag) {
    this.contextTags.add(contextTag);
  }

  /**
   * Returns the idle timeout.
   *
   * @return the idle timeout in millis or null.
   */
  public @Nullable Long getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * Sets the idle timeout.
   *
   * @param idleTimeout the idle timeout in millis or null.
   */
  public void setIdleTimeout(final @Nullable Long idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  /**
   * Returns whether sending of client reports has been enabled.
   *
   * @return true if enabled; false if disabled
   */
  public boolean isSendClientReports() {
    return sendClientReports;
  }

  /**
   * Enables / disables sending of client reports.
   *
   * @param sendClientReports true enables client reports; false disables them
   */
  public void setSendClientReports(boolean sendClientReports) {
    this.sendClientReports = sendClientReports;

    if (sendClientReports) {
      clientReportRecorder = new ClientReportRecorder(this);
    } else {
      clientReportRecorder = new NoOpClientReportRecorder();
    }
  }

  public boolean isEnableUserInteractionTracing() {
    return enableUserInteractionTracing;
  }

  public void setEnableUserInteractionTracing(boolean enableUserInteractionTracing) {
    this.enableUserInteractionTracing = enableUserInteractionTracing;
  }

  public boolean isEnableUserInteractionBreadcrumbs() {
    return enableUserInteractionBreadcrumbs;
  }

  public void setEnableUserInteractionBreadcrumbs(boolean enableUserInteractionBreadcrumbs) {
    this.enableUserInteractionBreadcrumbs = enableUserInteractionBreadcrumbs;
  }

  /**
   * Sets the instrumenter used for performance instrumentation.
   *
   * <p>If you set this to something other than {@link Instrumenter#SENTRY} Sentry will not create
   * any transactions automatically nor will it create transactions if you call
   * startTransaction(...), nor will it create child spans if you call startChild(...)
   *
   * @param instrumenter - the instrumenter to use
   */
  public void setInstrumenter(final @NotNull Instrumenter instrumenter) {
    this.instrumenter = instrumenter;
  }

  /**
   * Returns the instrumenter used for performance instrumentation
   *
   * @return the configured instrumenter
   */
  public @NotNull Instrumenter getInstrumenter() {
    return instrumenter;
  }

  /**
   * Returns a ClientReportRecorder or a NoOp if sending of client reports has been disabled.
   *
   * @return a client report recorder or NoOp
   */
  @ApiStatus.Internal
  public @NotNull IClientReportRecorder getClientReportRecorder() {
    return clientReportRecorder;
  }

  /**
   * Returns a ModulesLoader to load external modules (dependencies/packages) of the program.
   *
   * @return a modules loader or no-op
   */
  @ApiStatus.Internal
  public @NotNull IModulesLoader getModulesLoader() {
    return modulesLoader;
  }

  @ApiStatus.Internal
  public void setModulesLoader(final @Nullable IModulesLoader modulesLoader) {
    this.modulesLoader = modulesLoader != null ? modulesLoader : NoOpModulesLoader.getInstance();
  }

  /**
   * Returns a DebugMetaLoader to load sentry-debug-meta.properties containing ProGuard UUID, source
   * bundle IDs etc.
   *
   * @return a loader or no-op
   */
  @ApiStatus.Internal
  public @NotNull IDebugMetaLoader getDebugMetaLoader() {
    return debugMetaLoader;
  }

  @ApiStatus.Internal
  public void setDebugMetaLoader(final @Nullable IDebugMetaLoader debugMetaLoader) {
    this.debugMetaLoader =
        debugMetaLoader != null ? debugMetaLoader : NoOpDebugMetaLoader.getInstance();
  }

  /**
   * Returns a list of all {@link GestureTargetLocator} instances used to determine which {@link
   * io.sentry.internal.gestures.UiElement} was part of an user interaction.
   *
   * @return a list of {@link GestureTargetLocator}
   */
  public List<GestureTargetLocator> getGestureTargetLocators() {
    return gestureTargetLocators;
  }

  /**
   * Sets the list of {@link GestureTargetLocator} being used to determine relevant {@link
   * io.sentry.internal.gestures.UiElement} for user interactions.
   *
   * @param locators a list of {@link GestureTargetLocator}
   */
  public void setGestureTargetLocators(@NotNull final List<GestureTargetLocator> locators) {
    gestureTargetLocators.clear();
    gestureTargetLocators.addAll(locators);
  }

  /**
   * Returns a list of all {@link ViewHierarchyExporter} instances used to export view hierarchy
   * information.
   *
   * @return a list of {@link ViewHierarchyExporter}
   */
  @NotNull
  public final List<ViewHierarchyExporter> getViewHierarchyExporters() {
    return viewHierarchyExporters;
  }

  /**
   * Sets the list of {@link ViewHierarchyExporter} being used to export the view hierarchy.
   *
   * @param exporters a list of {@link ViewHierarchyExporter}
   */
  public void setViewHierarchyExporters(@NotNull final List<ViewHierarchyExporter> exporters) {
    viewHierarchyExporters.clear();
    viewHierarchyExporters.addAll(exporters);
  }

  public @NotNull IMainThreadChecker getMainThreadChecker() {
    return mainThreadChecker;
  }

  public void setMainThreadChecker(final @NotNull IMainThreadChecker mainThreadChecker) {
    this.mainThreadChecker = mainThreadChecker;
  }

  /**
   * Gets the performance collector used to collect performance stats while transactions run.
   *
   * @return the performance collector.
   */
  @ApiStatus.Internal
  public @NotNull TransactionPerformanceCollector getTransactionPerformanceCollector() {
    return transactionPerformanceCollector;
  }

  /**
   * Sets the performance collector used to collect performance stats while transactions run.
   *
   * @param transactionPerformanceCollector the performance collector.
   */
  @ApiStatus.Internal
  public void setTransactionPerformanceCollector(
      final @NotNull TransactionPerformanceCollector transactionPerformanceCollector) {
    this.transactionPerformanceCollector = transactionPerformanceCollector;
  }

  /**
   * Gets if the time-to-full-display spans is tracked in navigation transactions.
   *
   * @return if the time-to-full-display is tracked.
   */
  public boolean isEnableTimeToFullDisplayTracing() {
    return enableTimeToFullDisplayTracing;
  }

  /**
   * Sets if the time-to-full-display spans should be tracked in navigation transactions.
   *
   * @param enableTimeToFullDisplayTracing if the time-to-full-display spans should be tracked.
   */
  public void setEnableTimeToFullDisplayTracing(final boolean enableTimeToFullDisplayTracing) {
    this.enableTimeToFullDisplayTracing = enableTimeToFullDisplayTracing;
  }

  /**
   * Gets the reporter to call when a screen is fully loaded, used for time-to-full-display spans.
   *
   * @return The reporter to call when a screen is fully loaded.
   */
  @ApiStatus.Internal
  public @NotNull FullyDisplayedReporter getFullyDisplayedReporter() {
    return fullyDisplayedReporter;
  }

  /**
   * Whether OPTIONS requests should be traced.
   *
   * @return true if OPTIONS requests should be traced
   */
  public boolean isTraceOptionsRequests() {
    return traceOptionsRequests;
  }

  /**
   * Whether OPTIONS requests should be traced.
   *
   * @param traceOptionsRequests true if OPTIONS requests should be traced
   */
  public void setTraceOptionsRequests(boolean traceOptionsRequests) {
    this.traceOptionsRequests = traceOptionsRequests;
  }

  /**
   * Whether Sentry is enabled.
   *
   * @return true if Sentry should be enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Whether Sentry should be enabled.
   *
   * @param enabled true if Sentry should be enabled
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Whether to format serialized data, e.g. events logged to console in debug mode
   *
   * @return true if data should be pretty printed
   */
  public boolean isEnablePrettySerializationOutput() {
    return enablePrettySerializationOutput;
  }

  /**
   * Whether to send modules containing information about versions.
   *
   * @return true if modules should be sent.
   */
  public boolean isSendModules() {
    return sendModules;
  }

  /**
   * Whether to format serialized data, e.g. events logged to console in debug mode
   *
   * @param enablePrettySerializationOutput true if output should be pretty printed
   */
  public void setEnablePrettySerializationOutput(boolean enablePrettySerializationOutput) {
    this.enablePrettySerializationOutput = enablePrettySerializationOutput;
  }

  /**
   * Whether to profile app launches, depending on profilesSampler or profilesSampleRate. Depends on
   * {@link SentryOptions#isProfilingEnabled()}
   *
   * @return true if app launches should be profiled.
   */
  public boolean isEnableAppStartProfiling() {
    return isProfilingEnabled() && enableAppStartProfiling;
  }

  /**
   * Whether to profile app launches, depending on profilesSampler or profilesSampleRate.
   *
   * @param enableAppStartProfiling true if app launches should be profiled.
   */
  public void setEnableAppStartProfiling(boolean enableAppStartProfiling) {
    this.enableAppStartProfiling = enableAppStartProfiling;
  }

  /**
   * Whether to send modules containing information about versions.
   *
   * @param sendModules true if modules should be sent.
   */
  public void setSendModules(boolean sendModules) {
    this.sendModules = sendModules;
  }

  @ApiStatus.Experimental
  public void setIgnoredCheckIns(final @Nullable List<String> ignoredCheckIns) {
    if (ignoredCheckIns == null) {
      this.ignoredCheckIns = null;
    } else {
      @NotNull final List<String> filteredIgnoredCheckIns = new ArrayList<>();
      for (String slug : ignoredCheckIns) {
        if (!slug.isEmpty()) {
          filteredIgnoredCheckIns.add(slug);
        }
      }

      this.ignoredCheckIns = filteredIgnoredCheckIns;
    }
  }

  @ApiStatus.Experimental
  public @Nullable List<String> getIgnoredCheckIns() {
    return ignoredCheckIns;
  }

  /** Returns the current {@link SentryDateProvider} that is used to retrieve the current date. */
  @ApiStatus.Internal
  public @NotNull SentryDateProvider getDateProvider() {
    return dateProvider;
  }

  /**
   * Sets the {@link SentryDateProvider} which is used to retrieve the current date.
   *
   * <p>Different providers offer different precision. By default Sentry tries to offer the highest
   * precision available for the system.
   */
  @ApiStatus.Internal
  public void setDateProvider(final @NotNull SentryDateProvider dateProvider) {
    this.dateProvider = dateProvider;
  }

  /**
   * Adds a ICollector.
   *
   * @param collector the ICollector.
   */
  @ApiStatus.Internal
  public void addPerformanceCollector(final @NotNull IPerformanceCollector collector) {
    performanceCollectors.add(collector);
  }

  /**
   * Returns the list of ICollectors.
   *
   * @return the IPerformanceCollector list.
   */
  @ApiStatus.Internal
  public @NotNull List<IPerformanceCollector> getPerformanceCollectors() {
    return performanceCollectors;
  }

  @NotNull
  public IConnectionStatusProvider getConnectionStatusProvider() {
    return connectionStatusProvider;
  }

  public void setConnectionStatusProvider(
      final @NotNull IConnectionStatusProvider connectionStatusProvider) {
    this.connectionStatusProvider = connectionStatusProvider;
  }

  @ApiStatus.Internal
  @NotNull
  public IBackpressureMonitor getBackpressureMonitor() {
    return backpressureMonitor;
  }

  @ApiStatus.Internal
  public void setBackpressureMonitor(final @NotNull IBackpressureMonitor backpressureMonitor) {
    this.backpressureMonitor = backpressureMonitor;
  }

  @ApiStatus.Experimental
  public void setEnableBackpressureHandling(final boolean enableBackpressureHandling) {
    this.enableBackpressureHandling = enableBackpressureHandling;
  }

  /**
   * Returns the rate the profiler will sample rates at. 100 hz means 100 traces in 1 second.
   *
   * @return Rate the profiler will sample rates at.
   */
  @ApiStatus.Internal
  public int getProfilingTracesHz() {
    return profilingTracesHz;
  }

  /** Sets the rate the profiler will sample rates at. 100 hz means 100 traces in 1 second. */
  @ApiStatus.Internal
  public void setProfilingTracesHz(final int profilingTracesHz) {
    this.profilingTracesHz = profilingTracesHz;
  }

  @ApiStatus.Experimental
  public boolean isEnableBackpressureHandling() {
    return enableBackpressureHandling;
  }

  @ApiStatus.Internal
  public long getSessionFlushTimeoutMillis() {
    return sessionFlushTimeoutMillis;
  }

  @ApiStatus.Internal
  public void setSessionFlushTimeoutMillis(final long sessionFlushTimeoutMillis) {
    this.sessionFlushTimeoutMillis = sessionFlushTimeoutMillis;
  }

  @ApiStatus.Internal
  @Nullable
  public BeforeEnvelopeCallback getBeforeEnvelopeCallback() {
    return beforeEnvelopeCallback;
  }

  @ApiStatus.Internal
  public void setBeforeEnvelopeCallback(
      @Nullable final BeforeEnvelopeCallback beforeEnvelopeCallback) {
    this.beforeEnvelopeCallback = beforeEnvelopeCallback;
  }

  @ApiStatus.Experimental
  @Nullable
  public String getSpotlightConnectionUrl() {
    return spotlightConnectionUrl;
  }

  @ApiStatus.Experimental
  public void setSpotlightConnectionUrl(final @Nullable String spotlightConnectionUrl) {
    this.spotlightConnectionUrl = spotlightConnectionUrl;
  }

  @ApiStatus.Experimental
  public boolean isEnableSpotlight() {
    return enableSpotlight;
  }

  @ApiStatus.Experimental
  public void setEnableSpotlight(final boolean enableSpotlight) {
    this.enableSpotlight = enableSpotlight;
  }

  public boolean isEnableScopePersistence() {
    return enableScopePersistence;
  }

  public void setEnableScopePersistence(final boolean enableScopePersistence) {
    this.enableScopePersistence = enableScopePersistence;
  }

  @ApiStatus.Experimental
  public boolean isEnableMetrics() {
    return enableMetrics;
  }

  @ApiStatus.Experimental
  public void setEnableMetrics(boolean enableMetrics) {
    this.enableMetrics = enableMetrics;
  }

  /** The BeforeSend callback */
  public interface BeforeSendCallback {

    /**
     * Mutates or drop an event before being sent
     *
     * @param event the event
     * @param hint the hints
     * @return the original event or the mutated event or null if event was dropped
     */
    @Nullable
    SentryEvent execute(@NotNull SentryEvent event, @NotNull Hint hint);
  }

  /** The BeforeSendTransaction callback */
  public interface BeforeSendTransactionCallback {

    /**
     * Mutates or drop a transaction before being sent
     *
     * @param transaction the transaction
     * @param hint the hints
     * @return the original transaction or the mutated transaction or null if transaction was
     *     dropped
     */
    @Nullable
    SentryTransaction execute(@NotNull SentryTransaction transaction, @NotNull Hint hint);
  }

  /** The BeforeBreadcrumb callback */
  public interface BeforeBreadcrumbCallback {

    /**
     * Mutates or drop a callback before being added
     *
     * @param breadcrumb the breadcrumb
     * @param hint the hints, usually the source of the breadcrumb
     * @return the original breadcrumb or the mutated breadcrumb of null if breadcrumb was dropped
     */
    @Nullable
    Breadcrumb execute(@NotNull Breadcrumb breadcrumb, @NotNull Hint hint);
  }

  /** The traces sampler callback. */
  public interface TracesSamplerCallback {

    /**
     * Calculates the sampling value used to determine if transaction is going to be sent to Sentry
     * backend.
     *
     * @param samplingContext the sampling context
     * @return sampling value or {@code null} if decision has not been taken
     */
    @Nullable
    Double sample(@NotNull SamplingContext samplingContext);
  }

  /** The profiles sampler callback. */
  public interface ProfilesSamplerCallback {

    /**
     * Calculates the sampling value used to determine if a profile is going to be sent to Sentry
     * backend.
     *
     * @param samplingContext the sampling context
     * @return sampling value or {@code null} if decision has not been taken
     */
    @Nullable
    Double sample(@NotNull SamplingContext samplingContext);
  }

  /** The BeforeEnvelope callback */
  @ApiStatus.Internal
  public interface BeforeEnvelopeCallback {

    /**
     * A callback which gets called right before an envelope is about to be sent
     *
     * @param envelope the envelope
     * @param hint the hints
     */
    void execute(@NotNull SentryEnvelope envelope, @Nullable Hint hint);
  }

  /**
   * Creates SentryOptions instance without initializing any of the internal parts.
   *
   * <p>Used by {@link NoOpHub}.
   *
   * @return SentryOptions
   */
  @ApiStatus.Internal
  public static @NotNull SentryOptions empty() {
    return new SentryOptions(true);
  }

  /** SentryOptions ctor It adds and set default things */
  public SentryOptions() {
    this(false);
  }

  /**
   * Creates SentryOptions instance without initializing any of the internal parts.
   *
   * @param empty if options should be empty.
   */
  private SentryOptions(final boolean empty) {
    if (!empty) {
      // SentryExecutorService should be initialized before any
      // SendCachedEventFireAndForgetIntegration
      executorService = new SentryExecutorService();

      // UncaughtExceptionHandlerIntegration should be inited before any other Integration.
      // if there's an error on the setup, we are able to capture it
      integrations.add(new UncaughtExceptionHandlerIntegration());

      integrations.add(new ShutdownHookIntegration());
      integrations.add(new SpotlightIntegration());

      eventProcessors.add(new MainEventProcessor(this));
      eventProcessors.add(new DuplicateEventDetectionEventProcessor(this));

      if (Platform.isJvm()) {
        eventProcessors.add(new SentryRuntimeEventProcessor());
      }

      setSentryClientName(BuildConfig.SENTRY_JAVA_SDK_NAME + "/" + BuildConfig.VERSION_NAME);
      setSdkVersion(createSdkVersion());
      addPackageInfo();
    }
  }

  /**
   * Merges with another {@link SentryOptions} object. Used when loading additional options from
   * external locations.
   *
   * @param options options loaded from external locations
   */
  public void merge(final @NotNull ExternalOptions options) {
    if (options.getDsn() != null) {
      setDsn(options.getDsn());
    }
    if (options.getEnvironment() != null) {
      setEnvironment(options.getEnvironment());
    }
    if (options.getRelease() != null) {
      setRelease(options.getRelease());
    }
    if (options.getDist() != null) {
      setDist(options.getDist());
    }
    if (options.getServerName() != null) {
      setServerName(options.getServerName());
    }
    if (options.getProxy() != null) {
      setProxy(options.getProxy());
    }
    if (options.getEnableUncaughtExceptionHandler() != null) {
      setEnableUncaughtExceptionHandler(options.getEnableUncaughtExceptionHandler());
    }
    if (options.getPrintUncaughtStackTrace() != null) {
      setPrintUncaughtStackTrace(options.getPrintUncaughtStackTrace());
    }
    if (options.getEnableTracing() != null) {
      setEnableTracing(options.getEnableTracing());
    }
    if (options.getTracesSampleRate() != null) {
      setTracesSampleRate(options.getTracesSampleRate());
    }
    if (options.getProfilesSampleRate() != null) {
      setProfilesSampleRate(options.getProfilesSampleRate());
    }
    if (options.getDebug() != null) {
      setDebug(options.getDebug());
    }
    if (options.getEnableDeduplication() != null) {
      setEnableDeduplication(options.getEnableDeduplication());
    }
    if (options.getSendClientReports() != null) {
      setSendClientReports(options.getSendClientReports());
    }
    final Map<String, String> tags = new HashMap<>(options.getTags());
    for (final Map.Entry<String, String> tag : tags.entrySet()) {
      this.tags.put(tag.getKey(), tag.getValue());
    }
    final List<String> inAppIncludes = new ArrayList<>(options.getInAppIncludes());
    for (final String inAppInclude : inAppIncludes) {
      addInAppInclude(inAppInclude);
    }
    final List<String> inAppExcludes = new ArrayList<>(options.getInAppExcludes());
    for (final String inAppExclude : inAppExcludes) {
      addInAppExclude(inAppExclude);
    }
    for (final Class<? extends Throwable> exceptionType :
        new HashSet<>(options.getIgnoredExceptionsForType())) {
      addIgnoredExceptionForType(exceptionType);
    }
    if (options.getTracePropagationTargets() != null) {
      final List<String> tracePropagationTargets =
          new ArrayList<>(options.getTracePropagationTargets());
      setTracePropagationTargets(tracePropagationTargets);
    }
    final List<String> contextTags = new ArrayList<>(options.getContextTags());
    for (final String contextTag : contextTags) {
      addContextTag(contextTag);
    }
    if (options.getProguardUuid() != null) {
      setProguardUuid(options.getProguardUuid());
    }
    if (options.getIdleTimeout() != null) {
      setIdleTimeout(options.getIdleTimeout());
    }
    for (String bundleId : options.getBundleIds()) {
      addBundleId(bundleId);
    }

    if (options.isEnabled() != null) {
      setEnabled(options.isEnabled());
    }
    if (options.isEnablePrettySerializationOutput() != null) {
      setEnablePrettySerializationOutput(options.isEnablePrettySerializationOutput());
    }

    if (options.isSendModules() != null) {
      setSendModules(options.isSendModules());
    }
    if (options.getIgnoredCheckIns() != null) {
      final List<String> ignoredCheckIns = new ArrayList<>(options.getIgnoredCheckIns());
      setIgnoredCheckIns(ignoredCheckIns);
    }
    if (options.isEnableBackpressureHandling() != null) {
      setEnableBackpressureHandling(options.isEnableBackpressureHandling());
    }
  }

  private @NotNull SdkVersion createSdkVersion() {
    final String version = BuildConfig.VERSION_NAME;
    final SdkVersion sdkVersion = new SdkVersion(BuildConfig.SENTRY_JAVA_SDK_NAME, version);

    sdkVersion.setVersion(version);

    return sdkVersion;
  }

  private void addPackageInfo() {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry", BuildConfig.VERSION_NAME);
  }

  public static final class Proxy {
    private @Nullable String host;
    private @Nullable String port;
    private @Nullable String user;
    private @Nullable String pass;

    public Proxy(
        final @Nullable String host,
        final @Nullable String port,
        final @Nullable String user,
        final @Nullable String pass) {
      this.host = host;
      this.port = port;
      this.user = user;
      this.pass = pass;
    }

    public Proxy() {
      this(null, null, null, null);
    }

    public Proxy(@Nullable String host, @Nullable String port) {
      this(host, port, null, null);
    }

    public @Nullable String getHost() {
      return host;
    }

    public void setHost(final @Nullable String host) {
      this.host = host;
    }

    public @Nullable String getPort() {
      return port;
    }

    public void setPort(final @Nullable String port) {
      this.port = port;
    }

    public @Nullable String getUser() {
      return user;
    }

    public void setUser(final @Nullable String user) {
      this.user = user;
    }

    public @Nullable String getPass() {
      return pass;
    }

    public void setPass(final @Nullable String pass) {
      this.pass = pass;
    }
  }

  public enum RequestSize {
    NONE,
    SMALL,
    MEDIUM,
    ALWAYS,
  }
}
