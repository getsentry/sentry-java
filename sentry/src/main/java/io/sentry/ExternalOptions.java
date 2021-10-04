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
  private @Nullable Double tracesSampleRate;
  private @Nullable SentryOptions.RequestSize maxRequestBodySize;
  private final @NotNull Map<String, @NotNull String> tags = new ConcurrentHashMap<>();
  private @Nullable SentryOptions.Proxy proxy;
  private final @NotNull List<String> inAppExcludes = new CopyOnWriteArrayList<>();
  private final @NotNull List<String> inAppIncludes = new CopyOnWriteArrayList<>();
  private final @NotNull List<String> tracingOrigins = new CopyOnWriteArrayList<>();
  private @Nullable String proguardUuid;
  private final @NotNull Set<Class<? extends Throwable>> ignoredExceptionsForType =
      new CopyOnWriteArraySet<>();

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
    options.setTracesSampleRate(propertiesProvider.getDoubleProperty("traces-sample-rate"));
    options.setDebug(propertiesProvider.getBooleanProperty("debug"));
    options.setEnableDeduplication(propertiesProvider.getBooleanProperty("enable-deduplication"));
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

  public @NotNull List<String> getTracingOrigins() {
    return tracingOrigins;
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

  public @Nullable Double getTracesSampleRate() {
    return tracesSampleRate;
  }

  public void setTracesSampleRate(final @Nullable Double tracesSampleRate) {
    this.tracesSampleRate = tracesSampleRate;
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

  public void addTracingOrigin(final @NotNull String tracingOrigin) {
    this.tracingOrigins.add(tracingOrigin);
  }

  public void addIgnoredExceptionForType(final @NotNull Class<? extends Throwable> exceptionType) {
    this.ignoredExceptionsForType.add(exceptionType);
  }

  public void setTag(final @NotNull String key, final @NotNull String value) {
    this.tags.put(key, value);
  }
}
