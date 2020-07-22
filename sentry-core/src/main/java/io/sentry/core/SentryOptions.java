package io.sentry.core;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.cache.IEnvelopeCache;
import io.sentry.core.cache.IEventCache;
import io.sentry.core.protocol.SdkVersion;
import io.sentry.core.transport.ITransport;
import io.sentry.core.transport.ITransportGate;
import io.sentry.core.transport.NoOpEnvelopeCache;
import io.sentry.core.transport.NoOpEventCache;
import io.sentry.core.transport.NoOpTransport;
import io.sentry.core.transport.NoOpTransportGate;
import java.io.File;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sentry SDK options */
@Open
public class SentryOptions {

  /** Default Log level if not specified Default is DEBUG */
  static final SentryLevel DEFAULT_DIAGNOSTIC_LEVEL = SentryLevel.DEBUG;

  /**
   * Are callbacks that run for every event. They can either return a new event which in most cases
   * means just adding data OR return null in case the event will be dropped and not sent.
   */
  private final @NotNull List<EventProcessor> eventProcessors = new CopyOnWriteArrayList<>();

  /**
   * Code that provides middlewares, bindings or hooks into certain frameworks or environments,
   * along with code that inserts those bindings and activates them.
   */
  private final @NotNull List<Integration> integrations = new CopyOnWriteArrayList<>();

  /**
   * The DSN tells the SDK where to send the events to. If this value is not provided, the SDK will
   * just not send any events.
   */
  private @Nullable String dsn;

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
   * Turns debug mode on or off. If debug is enabled SDK will attempt to print out useful debugging
   * information if something goes wrong. Default is disabled.
   */
  private boolean debug;

  /** Turns NDK on or off. Default is enabled. */
  private boolean enableNdk = true;

  /** Logger interface to log useful debugging information if debug is enabled */
  private @NotNull ILogger logger = NoOpLogger.getInstance();

  /** minimum LogLevel to be used if debug is enabled */
  private @NotNull SentryLevel diagnosticLevel = DEFAULT_DIAGNOSTIC_LEVEL;

  /** Serializer interface to serialize/deserialize json events */
  private @NotNull ISerializer serializer = NoOpSerializer.getInstance();

  private @NotNull IEnvelopeReader envelopeReader = new EnvelopeReader();

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
   * This function is called with an SDK specific breadcrumb object before the breadcrumb is added
   * to the scope. When nothing is returned from the function, the breadcrumb is dropped
   */
  private @Nullable BeforeBreadcrumbCallback beforeBreadcrumb;

  /** The cache dir. path for caching offline events */
  private @Nullable String cacheDirPath;

  /** The cache dir. size for capping the number of events Default is 10 */
  private int cacheDirSize = 10;

  /** The sessions dir. size for capping the number of envelopes Default is 100 */
  private int sessionsDirSize = 100;

  /** Max. queue size before flushing events/envelopes to the disk */
  private int maxQueueSize = cacheDirSize + sessionsDirSize;

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

  /** The transport is an internal construct of the client that abstracts away the event sending. */
  private @NotNull ITransport transport = NoOpTransport.getInstance();

  /**
   * Implementations of this interface serve as gatekeepers that allow or disallow sending of the
   * events
   */
  private @NotNull ITransportGate transportGate = NoOpTransportGate.getInstance();

  /** Sets the distribution. Think about it together with release and environment */
  private @Nullable String dist;

  /** When enabled, threads are automatically attached to all logged events. */
  private boolean attachThreads = true;

  /**
   * When enabled, stack traces are automatically attached to all threads logged. Stack traces are
   * always attached to exceptions but when this is set stack traces are also sent with threads
   */
  private boolean attachStacktrace;

  /** Whether to enable automatic session tracking. */
  private boolean enableSessionTracking;

  /**
   * The session tracking interval in millis. This is the interval to end a session if the App goes
   * to the background.
   */
  private long sessionTrackingIntervalMillis = 30000; // 30s

  /** The distinct Id (generated Guid) used for session tracking */
  private String distinctId;

  /** The server name used in the Sentry messages. */
  private String serverName;

  /*
  When enabled, Sentry installs UncaughtExceptionHandlerIntegration.
   */
  private boolean enableUncaughtExceptionHandler = true;

  /** Sentry Executor Service that sends cached events and envelopes on App. start. */
  private @NotNull ISentryExecutorService executorService;

  /** connection timeout in milliseconds. */
  private int connectionTimeoutMillis = 5000;

  /** read timeout in milliseconds */
  private int readTimeoutMillis = 5000;

  /** whether to ignore TLS errors */
  private boolean bypassSecurity = false;

  /** Reads and caches event json files in the disk */
  private @NotNull IEventCache eventDiskCache = NoOpEventCache.getInstance();

  /** Reads and caches envelope files in the disk */
  private @NotNull IEnvelopeCache envelopeDiskCache = NoOpEnvelopeCache.getInstance();

  /** SdkVersion object that contains the Sentry Client Name and its version */
  private @Nullable SdkVersion sdkVersion;

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
  public void setDsn(@Nullable String dsn) {
    this.dsn = dsn;
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
  public void setDebug(boolean debug) {
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

  public @NotNull IEnvelopeReader getEnvelopeReader() {
    return envelopeReader;
  }

  public void setEnvelopeReader(final @Nullable IEnvelopeReader envelopeReader) {
    this.envelopeReader =
        envelopeReader != null ? envelopeReader : NoOpEnvelopeReader.getInstance();
  }

  /**
   * Check if NDK is ON or OFF Default is ON
   *
   * @return true if ON or false otherwise
   */
  public boolean isEnableNdk() {
    return enableNdk;
  }

  /**
   * Sets NDK to ON or OFF
   *
   * @param enableNdk true if ON or false otherwise
   */
  public void setEnableNdk(boolean enableNdk) {
    this.enableNdk = enableNdk;
  }

  /**
   * Returns the shutdown timeout in Millis
   *
   * @return the timeout in Millis
   */
  public long getShutdownTimeout() {
    return shutdownTimeoutMillis;
  }

  /**
   * Sets the shutdown timeout in Millis Default is 2000 = 2s
   *
   * @param shutdownTimeoutMillis the shutdown timeout in millis
   */
  public void setShutdownTimeout(long shutdownTimeoutMillis) {
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
    return cacheDirPath;
  }

  /**
   * Returns the outbox path if cacheDirPath is set
   *
   * @return the outbox path or null if not set
   */
  public @Nullable String getOutboxPath() {
    if (cacheDirPath == null || cacheDirPath.isEmpty()) {
      return null;
    }
    return cacheDirPath + File.separator + "outbox";
  }

  /**
   * Returns the sessions path if cacheDirPath is set
   *
   * @return the sessions path or null if not set
   */
  public @Nullable String getSessionsPath() {
    if (cacheDirPath == null || cacheDirPath.isEmpty()) {
      return null;
    }
    return cacheDirPath + File.separator + "sessions";
  }

  /**
   * Sets the cache dir. path
   *
   * @param cacheDirPath the cache dir. path
   */
  public void setCacheDirPath(@Nullable String cacheDirPath) {
    this.cacheDirPath = cacheDirPath;
  }

  /**
   * Returns the cache dir. size Default is 10
   *
   * @return the cache dir. size
   */
  public int getCacheDirSize() {
    return cacheDirSize;
  }

  /**
   * Sets the cache dir. size Default is 10
   *
   * @param cacheDirSize the cache dir. size
   */
  public void setCacheDirSize(int cacheDirSize) {
    this.cacheDirSize = cacheDirSize;
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
   * @return the environment or null if not set
   */
  public @Nullable String getEnvironment() {
    return environment;
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
   * Sets the sampleRate Can be anything between 0.01 and 1.0 or null (default), to disable it.
   *
   * @param sampleRate the sample rate
   */
  public void setSampleRate(Double sampleRate) {
    if (sampleRate != null && (sampleRate > 1.0 || sampleRate <= 0.0)) {
      throw new IllegalArgumentException(
          "The value "
              + sampleRate
              + " is not valid. Use null to disable or values between 0.01 (inclusive) and 1.0 (exclusive).");
    }
    this.sampleRate = sampleRate;
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
   * Returns the Transport interface
   *
   * @return the transport
   */
  public @NotNull ITransport getTransport() {
    return transport;
  }

  /**
   * Sets the Transport interface
   *
   * @param transport the transport
   */
  public void setTransport(@Nullable ITransport transport) {
    this.transport = transport != null ? transport : NoOpTransport.getInstance();
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
  public boolean isEnableSessionTracking() {
    return enableSessionTracking;
  }

  /**
   * Enable or disable the automatic session tracking
   *
   * @param enableSessionTracking true if enabled or false otherwise
   */
  public void setEnableSessionTracking(boolean enableSessionTracking) {
    this.enableSessionTracking = enableSessionTracking;
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
   * Returns the sessions dir size
   *
   * @return the dir size
   */
  public int getSessionsDirSize() {
    return sessionsDirSize;
  }

  /**
   * Sets the sessions dir size
   *
   * @param sessionsDirSize the sessions dir size
   */
  public void setSessionsDirSize(int sessionsDirSize) {
    this.sessionsDirSize = sessionsDirSize;
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
  public String getDistinctId() {
    return distinctId;
  }

  /**
   * Sets the distinct Id
   *
   * @param distinctId the distinct Id
   */
  @ApiStatus.Internal
  public void setDistinctId(String distinctId) {
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
  public void setEnableUncaughtExceptionHandler(boolean enableUncaughtExceptionHandler) {
    this.enableUncaughtExceptionHandler = enableUncaughtExceptionHandler;
  }

  /**
   * Returns the SentryExecutorService
   *
   * @return the SentryExecutorService
   */
  @NotNull
  ISentryExecutorService getExecutorService() {
    return executorService;
  }

  /**
   * Sets the SentryExecutorService
   *
   * @param executorService the SentryExecutorService
   */
  void setExecutorService(final @NotNull ISentryExecutorService executorService) {
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
   * Returns whether to ignore TLS errors
   *
   * @return the bypassSecurity
   */
  public boolean isBypassSecurity() {
    return bypassSecurity;
  }

  /**
   * Sets whether to ignore TLS errors
   *
   * @param bypassSecurity the bypassSecurity
   */
  public void setBypassSecurity(boolean bypassSecurity) {
    this.bypassSecurity = bypassSecurity;
  }

  /**
   * Returns the EventCache interface
   *
   * @return the EventCache object
   */
  public @NotNull IEventCache getEventDiskCache() {
    return eventDiskCache;
  }

  /**
   * Sets the EventCache interface
   *
   * @param eventDiskCache the EventCache object
   */
  public void setEventDiskCache(final @Nullable IEventCache eventDiskCache) {
    this.eventDiskCache = eventDiskCache != null ? eventDiskCache : NoOpEventCache.getInstance();
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
   * Sets the SdkVersion object
   *
   * @param sdkVersion the SdkVersion object or null
   */
  @ApiStatus.Internal
  public void setSdkVersion(final @Nullable SdkVersion sdkVersion) {
    this.sdkVersion = sdkVersion;
  }

  /** The BeforeSend callback */
  public interface BeforeSendCallback {

    /**
     * Mutates or drop an event before being sent
     *
     * @param event the event
     * @param hint the hint, usually the source of the event
     * @return the original event or the mutated event or null if event was dropped
     */
    @Nullable
    SentryEvent execute(@NotNull SentryEvent event, @Nullable Object hint);
  }

  /** The BeforeBreadcrumb callback */
  public interface BeforeBreadcrumbCallback {

    /**
     * Mutates or drop a callback before being added
     *
     * @param breadcrumb the breadcrumb
     * @param hint the hint, usually the source of the breadcrumb
     * @return the original breadcrumb or the mutated breadcrumb of null if breadcrumb was dropped
     */
    @Nullable
    Breadcrumb execute(@NotNull Breadcrumb breadcrumb, @Nullable Object hint);
  }

  /** SentryOptions ctor It adds and set default things */
  public SentryOptions() {
    // SentryExecutorService should be inited before any SendCachedEventFireAndForgetIntegration
    executorService = new SentryExecutorService();

    // UncaughtExceptionHandlerIntegration should be inited before any other Integration.
    // if there's an error on the setup, we are able to capture it
    integrations.add(new UncaughtExceptionHandlerIntegration());

    eventProcessors.add(new MainEventProcessor(this));

    integrations.add(
        new SendCachedEventFireAndForgetIntegration(
            new SendFireAndForgetEventSender(() -> getCacheDirPath())));

    integrations.add(
        new SendCachedEventFireAndForgetIntegration(
            new SendFireAndForgetEnvelopeSender(() -> getSessionsPath())));
  }
}
