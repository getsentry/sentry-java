package io.sentry;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.cache.IEnvelopeCache;
import io.sentry.config.PropertiesProvider;
import io.sentry.protocol.SdkVersion;
import io.sentry.transport.ITransportGate;
import io.sentry.transport.NoOpEnvelopeCache;
import io.sentry.transport.NoOpTransportGate;
import io.sentry.util.Platform;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sentry SDK options */
@Open
public class SentryOptions {

  /** Default Log level if not specified Default is DEBUG */
  static final SentryLevel DEFAULT_DIAGNOSTIC_LEVEL = SentryLevel.DEBUG;

  /** The default HTTP proxy port to use if an HTTP Proxy hostname is set but port is not. */
  private static final String PROXY_PORT_DEFAULT = "80";

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
  private long shutdownTimeout = 2000; // 2s

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
  private @Nullable Boolean debug;

  /** Turns NDK on or off. Default is enabled. */
  private boolean enableNdk = true;

  /** Logger interface to log useful debugging information if debug is enabled */
  private @NotNull ILogger logger = NoOpLogger.getInstance();

  /** minimum LogLevel to be used if debug is enabled */
  private @NotNull SentryLevel diagnosticLevel = DEFAULT_DIAGNOSTIC_LEVEL;

  /** Envelope reader interface */
  private @NotNull IEnvelopeReader envelopeReader = new EnvelopeReader();

  /** Serializer interface to serialize/deserialize json events */
  private @NotNull ISerializer serializer = new GsonSerializer(this);

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
   * The transport factory creates instances of {@link io.sentry.transport.ITransport} - internal
   * construct of the client that abstracts away the event sending.
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

  /*
  When enabled, Sentry installs UncaughtExceptionHandlerIntegration.
   */
  private @Nullable Boolean enableUncaughtExceptionHandler = true;

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

  /** HostnameVerifier for self-signed certificate trust* */
  private @Nullable HostnameVerifier hostnameVerifier;

  /** SSLSocketFactory for self-signed certificate trust * */
  private @Nullable SSLSocketFactory sslSocketFactory;

  /** list of scope observers */
  private final @NotNull List<IScopeObserver> observers = new ArrayList<>();

  /** Enable the Java to NDK Scope sync */
  private boolean enableScopeSync;

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
  private @Nullable Boolean enableDeduplication = true;

  /** Maximum number of spans that can be atteched to single transaction. */
  private int maxSpans = 1000;

  /** Registers hook that flushes {@link Hub} when main thread shuts down. */
  private boolean enableShutdownHook = true;

  /**
   * Controls the size of the request body to extract if any. No truncation is done by the SDK. If
   * the request body is larger than the accepted size, nothing is sent.
   */
  private @NotNull RequestSize maxRequestBodySize = RequestSize.NONE;

  /** Controls if the `tracestate` header is attached to envelopes and HTTP client integrations. */
  private boolean traceSampling;

  /**
   * Contains a list of origins to which `sentry-trace` header should be sent in HTTP integrations.
   */
  private final @NotNull List<String> tracingOrigins = new CopyOnWriteArrayList<>();

  /** Proguard UUID. */
  private @Nullable String proguardUuid;

  /**
   * Creates {@link SentryOptions} from properties provided by a {@link PropertiesProvider}.
   *
   * @param propertiesProvider the properties provider
   * @return the sentry options
   */
  @SuppressWarnings("unchecked")
  public static @NotNull SentryOptions from(
      final @NotNull PropertiesProvider propertiesProvider, final @NotNull ILogger logger) {
    final SentryOptions options = new SentryOptions();
    options.setDsn(propertiesProvider.getProperty("dsn"));
    options.setEnvironment(propertiesProvider.getProperty("environment"));
    options.setRelease(propertiesProvider.getProperty("release"));
    options.setDist(propertiesProvider.getProperty("dist"));
    options.setServerName(propertiesProvider.getProperty("servername"));
    options.setEnableUncaughtExceptionHandler(
        propertiesProvider.getBooleanProperty("uncaught.handler.enabled"));
    options.setTracesSampleRate(propertiesProvider.getDoubleProperty("traces-sample-rate"));
    options.setDebug(propertiesProvider.getBooleanProperty("debug"));
    options.setEnableDeduplication(propertiesProvider.getBooleanProperty("enable-deduplication"));
    final String maxRequestBodySize = propertiesProvider.getProperty("max-request-body-size");
    if (maxRequestBodySize != null) {
      options.setMaxRequestBodySize(
          RequestSize.valueOf(maxRequestBodySize.toUpperCase(Locale.ROOT)));
    }
    final Map<String, String> tags = propertiesProvider.getMap("tags");
    for (final Map.Entry<String, String> tag : tags.entrySet()) {
      options.setTag(tag.getKey(), tag.getValue());
    }

    final String proxyHost = propertiesProvider.getProperty("proxy.host");
    final String proxyUser = propertiesProvider.getProperty("proxy.user");
    final String proxyPass = propertiesProvider.getProperty("proxy.pass");
    final String proxyPort = propertiesProvider.getProperty("proxy.port", PROXY_PORT_DEFAULT);

    if (proxyHost != null) {
      options.setProxy(new Proxy(proxyHost, proxyPort, proxyUser, proxyPass));
    }

    for (final String inAppInclude : propertiesProvider.getList("in-app-includes")) {
      options.addInAppInclude(inAppInclude);
    }
    for (final String inAppExclude : propertiesProvider.getList("in-app-excludes")) {
      options.addInAppExclude(inAppExclude);
    }
    for (final String tracingOrigin : propertiesProvider.getList("tracing-origins")) {
      options.addTracingOrigin(tracingOrigin);
    }
    options.setProguardUuid(propertiesProvider.getProperty("proguard-uuid"));

    for (final String ignoredExceptionType :
        propertiesProvider.getList("ignored-exceptions-for-type")) {
      try {
        Class<?> clazz = Class.forName(ignoredExceptionType);
        if (Throwable.class.isAssignableFrom(clazz)) {
          options.addIgnoredExceptionForType((Class<? extends Throwable>) clazz);
        } else {
          logger.log(
              SentryLevel.WARNING,
              "Skipping setting %s as ignored-exception-for-type. Reason: %s does not extend Throwable",
              ignoredExceptionType,
              ignoredExceptionType);
        }
      } catch (ClassNotFoundException e) {
        logger.log(
            SentryLevel.WARNING,
            "Skipping setting %s as ignored-exception-for-type. Reason: %s class is not found",
            ignoredExceptionType,
            ignoredExceptionType);
      }
    }
    return options;
  }

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
    return Boolean.TRUE.equals(debug);
  }

  /**
   * Sets the debug mode to ON or OFF Default is OFF
   *
   * @param debug true if ON or false otherwise
   */
  public void setDebug(final @Nullable Boolean debug) {
    this.debug = debug;
  }

  /**
   * Check if debug mode is ON, OFF or not set.
   *
   * @return true if ON or false otherwise
   */
  private @Nullable Boolean getDebug() {
    return debug;
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
    return shutdownTimeout;
  }

  /**
   * Sets the shutdown timeout in Millis Default is 2000 = 2s
   *
   * @param shutdownTimeoutMillis the shutdown timeout in millis
   */
  public void setShutdownTimeout(long shutdownTimeoutMillis) {
    this.shutdownTimeout = shutdownTimeoutMillis;
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
   * Sets the cache dir. path
   *
   * @param cacheDirPath the cache dir. path
   */
  public void setCacheDirPath(@Nullable String cacheDirPath) {
    this.cacheDirPath = cacheDirPath;
  }

  /**
   * Returns the cache dir. size Default is 30
   *
   * @deprecated use {{@link SentryOptions#getMaxCacheItems()} }
   * @return the cache dir. size
   */
  @Deprecated
  public int getCacheDirSize() {
    return maxCacheItems;
  }

  /**
   * Sets the cache dir. size Default is 30
   *
   * @deprecated use {{@link SentryOptions#setCacheDirSize(int)} }
   * @param cacheDirSize the cache dir. size
   */
  @Deprecated
  public void setCacheDirSize(int cacheDirSize) {
    maxCacheItems = cacheDirSize;
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
              + " is not valid. Use null to disable or values > 0.0 and <= 1.0.");
    }
    this.sampleRate = sampleRate;
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
    if (tracesSampleRate != null && (tracesSampleRate > 1.0 || tracesSampleRate < 0.0)) {
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

  /** @deprecated use {@link SentryOptions#isEnableAutoSessionTracking()} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public boolean isEnableSessionTracking() {
    return enableAutoSessionTracking;
  }

  /** @deprecated use {@link SentryOptions#setEnableAutoSessionTracking(boolean)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @SuppressWarnings("InlineMeSuggester")
  public void setEnableSessionTracking(final boolean enableSessionTracking) {
    setEnableAutoSessionTracking(enableSessionTracking);
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
    return Boolean.TRUE.equals(enableUncaughtExceptionHandler);
  }

  /**
   * Checks if the default UncaughtExceptionHandlerIntegration is enabled or disabled or not set.
   *
   * @return true if enabled, false otherwise or null if not set.
   */
  public @Nullable Boolean getEnableUncaughtExceptionHandler() {
    return enableUncaughtExceptionHandler;
  }

  /**
   * Enable or disable the default UncaughtExceptionHandlerIntegration.
   *
   * @param enableUncaughtExceptionHandler true if enabled or false otherwise.
   */
  public void setEnableUncaughtExceptionHandler(
      final @Nullable Boolean enableUncaughtExceptionHandler) {
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
   * Returns HostnameVerifier
   *
   * @return HostnameVerifier objecr or null
   */
  public @Nullable HostnameVerifier getHostnameVerifier() {
    return hostnameVerifier;
  }

  /**
   * Set custom HostnameVerifier
   *
   * @param hostnameVerifier the HostnameVerifier
   */
  public void setHostnameVerifier(final @Nullable HostnameVerifier hostnameVerifier) {
    this.hostnameVerifier = hostnameVerifier;
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
  List<IScopeObserver> getScopeObservers() {
    return observers;
  }

  /**
   * Returns if the Java to NDK Scope sync is enabled
   *
   * @return true if enabled or false otherwise
   */
  public boolean isEnableScopeSync() {
    return enableScopeSync;
  }

  /**
   * Enables or not the Java to NDK Scope sync
   *
   * @param enableScopeSync true if enabled or false otherwise
   */
  public void setEnableScopeSync(boolean enableScopeSync) {
    this.enableScopeSync = enableScopeSync;
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
    return Boolean.TRUE.equals(enableDeduplication);
  }

  /**
   * Returns if event deduplication is turned on or of or {@code null} if not specified.
   *
   * @return if event deduplication is turned on or of or {@code null} if not specified.
   */
  private @Nullable Boolean getEnableDeduplication() {
    return enableDeduplication;
  }

  /**
   * Enables or disables event deduplication.
   *
   * @param enableDeduplication true if enabled false otherwise
   */
  public void setEnableDeduplication(final @Nullable Boolean enableDeduplication) {
    this.enableDeduplication = enableDeduplication;
  }

  /**
   * Returns if tracing should be enabled. If tracing is disabled, starting transactions returns
   * {@link NoOpTransaction}.
   *
   * @return if tracing is enabled.
   */
  public boolean isTracingEnabled() {
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

  /** Note: this is an experimental API and will be removed without notice. */
  @ApiStatus.Experimental
  public boolean isTraceSampling() {
    return traceSampling;
  }

  /**
   * Note: this is an experimental API and will be removed without notice.
   *
   * @param traceSampling - if trace sampling should be enabled
   */
  @ApiStatus.Experimental
  public void setTraceSampling(boolean traceSampling) {
    this.traceSampling = traceSampling;
  }

  /**
   * Returns a list of origins to which `sentry-trace` header should be sent in HTTP integrations.
   *
   * @return the list of origins
   */
  public @NotNull List<String> getTracingOrigins() {
    return tracingOrigins;
  }

  /**
   * Adds an origin to which `sentry-trace` header should be sent in HTTP integrations.
   *
   * @param tracingOrigin - the tracing origin
   */
  public void addTracingOrigin(final @NotNull String tracingOrigin) {
    this.tracingOrigins.add(tracingOrigin);
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

  /**
   * Creates SentryOptions instance without initializing any of the internal parts.
   *
   * <p>Used by {@link NoOpHub}.
   *
   * @return SentryOptions
   */
  static @NotNull SentryOptions empty() {
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

      eventProcessors.add(new MainEventProcessor(this));
      eventProcessors.add(new DuplicateEventDetectionEventProcessor(this));

      if (Platform.isJvm()) {
        eventProcessors.add(new SentryRuntimeEventProcessor());
      }

      setSentryClientName(BuildConfig.SENTRY_JAVA_SDK_NAME + "/" + BuildConfig.VERSION_NAME);
      setSdkVersion(createSdkVersion());
    }
  }

  /**
   * Merges with another {@link SentryOptions} object. Used when loading additional options from
   * external locations.
   *
   * @param options options loaded from external locations
   */
  void merge(final @NotNull SentryOptions options) {
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
    if (options.getTracesSampleRate() != null) {
      setTracesSampleRate(options.getTracesSampleRate());
    }
    if (options.getDebug() != null) {
      setDebug(options.getDebug());
    }
    if (options.getEnableDeduplication() != null) {
      setEnableDeduplication(options.getEnableDeduplication());
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
    final List<String> tracingOrigins = new ArrayList<>(options.getTracingOrigins());
    for (final String tracingOrigin : tracingOrigins) {
      addTracingOrigin(tracingOrigin);
    }
    if (options.getProguardUuid() != null) {
      setProguardUuid(options.getProguardUuid());
    }
  }

  private @NotNull SdkVersion createSdkVersion() {
    final String version = BuildConfig.VERSION_NAME;
    final SdkVersion sdkVersion = new SdkVersion(BuildConfig.SENTRY_JAVA_SDK_NAME, version);

    sdkVersion.setVersion(version);
    sdkVersion.addPackage("maven:io.sentry:sentry", version);

    return sdkVersion;
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
