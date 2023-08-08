package io.sentry;

import io.sentry.config.PropertiesProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Externally bindable properties set on {@link SentryOptions}. */
public final class ExternalOptions {

  /** The default HTTP proxy port to use if an HTTP Proxy hostname is set but port is not. */
  private static final String PROXY_PORT_DEFAULT = "80";

  private @Nullable String dsn;
  private @Nullable String environment;
  private @Nullable String release;
  private @Nullable String dist;
  private @Nullable String serverName;
  private @Nullable Boolean enableUncaughtExceptionHandler;
  private @Nullable Boolean debug;
  private @Nullable Boolean enableDeduplication;
  private @Nullable Boolean enableTracing;
  private @Nullable Double tracesSampleRate;
  private @Nullable Double profilesSampleRate;
  private @Nullable SentryOptions.RequestSize maxRequestBodySize;
  private final @NotNull Map<String, @NotNull String> tags = new ConcurrentHashMap<>();
  private @Nullable SentryOptions.Proxy proxy;
  private final @NotNull List<String> inAppExcludes = new CopyOnWriteArrayList<>();
  private final @NotNull List<String> inAppIncludes = new CopyOnWriteArrayList<>();
  private @Nullable List<String> tracePropagationTargets = null;
  private final @NotNull List<String> contextTags = new CopyOnWriteArrayList<>();
  private @Nullable String proguardUuid;
  private @Nullable Long idleTimeout;
  private final @NotNull Set<Class<? extends Throwable>> ignoredExceptionsForType =
      new CopyOnWriteArraySet<>();
  private @Nullable Boolean printUncaughtStackTrace;
  private @Nullable Boolean sendClientReports;
  private @NotNull Set<String> bundleIds = new CopyOnWriteArraySet<>();
  private @Nullable Boolean enabled;

  @SuppressWarnings("unchecked")
  public static @NotNull ExternalOptions from(
      final @NotNull PropertiesProvider propertiesProvider, final @NotNull ILogger logger) {
    final ExternalOptions options = new ExternalOptions();
    options.setDsn(propertiesProvider.getProperty("dsn"));
    options.setEnvironment(propertiesProvider.getProperty("environment"));
    options.setRelease(propertiesProvider.getProperty("release"));
    options.setDist(propertiesProvider.getProperty("dist"));
    options.setServerName(propertiesProvider.getProperty("servername"));
    options.setEnableUncaughtExceptionHandler(
        propertiesProvider.getBooleanProperty("uncaught.handler.enabled"));
    options.setPrintUncaughtStackTrace(
        propertiesProvider.getBooleanProperty("uncaught.handler.print-stacktrace"));
    options.setEnableTracing(propertiesProvider.getBooleanProperty("enable-tracing"));
    options.setTracesSampleRate(propertiesProvider.getDoubleProperty("traces-sample-rate"));
    options.setProfilesSampleRate(propertiesProvider.getDoubleProperty("profiles-sample-rate"));
    options.setDebug(propertiesProvider.getBooleanProperty("debug"));
    options.setEnableDeduplication(propertiesProvider.getBooleanProperty("enable-deduplication"));
    options.setSendClientReports(propertiesProvider.getBooleanProperty("send-client-reports"));
    final String maxRequestBodySize = propertiesProvider.getProperty("max-request-body-size");
    if (maxRequestBodySize != null) {
      options.setMaxRequestBodySize(
          SentryOptions.RequestSize.valueOf(maxRequestBodySize.toUpperCase(Locale.ROOT)));
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
      options.setProxy(new SentryOptions.Proxy(proxyHost, proxyPort, proxyUser, proxyPass));
    }

    for (final String inAppInclude : propertiesProvider.getList("in-app-includes")) {
      options.addInAppInclude(inAppInclude);
    }
    for (final String inAppExclude : propertiesProvider.getList("in-app-excludes")) {
      options.addInAppExclude(inAppExclude);
    }

    @Nullable List<String> tracePropagationTargets = null;

    if (propertiesProvider.getProperty("trace-propagation-targets") != null) {
      tracePropagationTargets = propertiesProvider.getList("trace-propagation-targets");
    }

    // TODO: Remove once tracing-origins has been removed
    if (tracePropagationTargets == null
        && propertiesProvider.getProperty("tracing-origins") != null) {
      tracePropagationTargets = propertiesProvider.getList("tracing-origins");
    }

    if (tracePropagationTargets != null) {
      for (final String tracePropagationTarget : tracePropagationTargets) {
        options.addTracePropagationTarget(tracePropagationTarget);
      }
    }

    for (final String contextTag : propertiesProvider.getList("context-tags")) {
      options.addContextTag(contextTag);
    }
    options.setProguardUuid(propertiesProvider.getProperty("proguard-uuid"));
    for (final String bundleId : propertiesProvider.getList("bundle-ids")) {
      options.addBundleId(bundleId);
    }
    options.setIdleTimeout(propertiesProvider.getLongProperty("idle-timeout"));

    options.setEnabled(propertiesProvider.getBooleanProperty("enabled"));

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

  public @Nullable String getDsn() {
    return dsn;
  }

  public void setDsn(final @Nullable String dsn) {
    this.dsn = dsn;
  }

  public @Nullable String getEnvironment() {
    return environment;
  }

  public void setEnvironment(final @Nullable String environment) {
    this.environment = environment;
  }

  public @Nullable String getRelease() {
    return release;
  }

  public void setRelease(final @Nullable String release) {
    this.release = release;
  }

  public @Nullable String getDist() {
    return dist;
  }

  public void setDist(final @Nullable String dist) {
    this.dist = dist;
  }

  public @Nullable String getServerName() {
    return serverName;
  }

  public void setServerName(final @Nullable String serverName) {
    this.serverName = serverName;
  }

  public @Nullable Boolean getEnableUncaughtExceptionHandler() {
    return enableUncaughtExceptionHandler;
  }

  public void setEnableUncaughtExceptionHandler(
      final @Nullable Boolean enableUncaughtExceptionHandler) {
    this.enableUncaughtExceptionHandler = enableUncaughtExceptionHandler;
  }

  @Deprecated
  public @Nullable List<String> getTracingOrigins() {
    return tracePropagationTargets;
  }

  public @Nullable List<String> getTracePropagationTargets() {
    return tracePropagationTargets;
  }

  public @Nullable Boolean getDebug() {
    return debug;
  }

  public void setDebug(final @Nullable Boolean debug) {
    this.debug = debug;
  }

  public @Nullable Boolean getEnableDeduplication() {
    return enableDeduplication;
  }

  public void setEnableDeduplication(final @Nullable Boolean enableDeduplication) {
    this.enableDeduplication = enableDeduplication;
  }

  public @Nullable Boolean getEnableTracing() {
    return enableTracing;
  }

  public void setEnableTracing(final @Nullable Boolean enableTracing) {
    this.enableTracing = enableTracing;
  }

  public @Nullable Double getTracesSampleRate() {
    return tracesSampleRate;
  }

  public void setTracesSampleRate(final @Nullable Double tracesSampleRate) {
    this.tracesSampleRate = tracesSampleRate;
  }

  public @Nullable Double getProfilesSampleRate() {
    return profilesSampleRate;
  }

  public void setProfilesSampleRate(final @Nullable Double profilesSampleRate) {
    this.profilesSampleRate = profilesSampleRate;
  }

  public @Nullable SentryOptions.RequestSize getMaxRequestBodySize() {
    return maxRequestBodySize;
  }

  public void setMaxRequestBodySize(final @Nullable SentryOptions.RequestSize maxRequestBodySize) {
    this.maxRequestBodySize = maxRequestBodySize;
  }

  public @NotNull Map<String, String> getTags() {
    return tags;
  }

  public @Nullable SentryOptions.Proxy getProxy() {
    return proxy;
  }

  public void setProxy(final @Nullable SentryOptions.Proxy proxy) {
    this.proxy = proxy;
  }

  public @NotNull List<String> getInAppExcludes() {
    return inAppExcludes;
  }

  public @NotNull List<String> getInAppIncludes() {
    return inAppIncludes;
  }

  public @NotNull List<String> getContextTags() {
    return contextTags;
  }

  public @Nullable String getProguardUuid() {
    return proguardUuid;
  }

  public void setProguardUuid(final @Nullable String proguardUuid) {
    this.proguardUuid = proguardUuid;
  }

  public @NotNull Set<Class<? extends Throwable>> getIgnoredExceptionsForType() {
    return ignoredExceptionsForType;
  }

  public void addInAppInclude(final @NotNull String include) {
    inAppIncludes.add(include);
  }

  public void addInAppExclude(final @NotNull String exclude) {
    inAppExcludes.add(exclude);
  }

  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public void addTracingOrigin(final @NotNull String tracingOrigin) {
    this.addTracePropagationTarget(tracingOrigin);
  }

  public void addTracePropagationTarget(final @NotNull String tracePropagationTarget) {
    if (tracePropagationTargets == null) {
      tracePropagationTargets = new CopyOnWriteArrayList<>();
    }
    if (!tracePropagationTarget.isEmpty()) {
      this.tracePropagationTargets.add(tracePropagationTarget);
    }
  }

  public void addContextTag(final @NotNull String contextTag) {
    this.contextTags.add(contextTag);
  }

  public void addIgnoredExceptionForType(final @NotNull Class<? extends Throwable> exceptionType) {
    this.ignoredExceptionsForType.add(exceptionType);
  }

  public void setTag(final @NotNull String key, final @NotNull String value) {
    this.tags.put(key, value);
  }

  public @Nullable Boolean getPrintUncaughtStackTrace() {
    return printUncaughtStackTrace;
  }

  public void setPrintUncaughtStackTrace(final @Nullable Boolean printUncaughtStackTrace) {
    this.printUncaughtStackTrace = printUncaughtStackTrace;
  }

  public @Nullable Long getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(final @Nullable Long idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  public @Nullable Boolean getSendClientReports() {
    return sendClientReports;
  }

  public void setSendClientReports(final @Nullable Boolean sendClientReports) {
    this.sendClientReports = sendClientReports;
  }

  public @NotNull Set<String> getBundleIds() {
    return bundleIds;
  }

  public void addBundleId(final @NotNull String bundleId) {
    bundleIds.add(bundleId);
  }

  public @Nullable Boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final @Nullable Boolean enabled) {
    this.enabled = enabled;
  }
}
