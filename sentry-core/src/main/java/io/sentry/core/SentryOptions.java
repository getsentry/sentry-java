package io.sentry.core;

import static io.sentry.core.ILogger.logIfNotNull;

import com.jakewharton.nopen.annotation.Open;
import java.io.File;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class SentryOptions {
  static final SentryLevel DEFAULT_DIAGNOSTIC_LEVEL = SentryLevel.DEBUG;

  private final @NotNull List<EventProcessor> eventProcessors = new ArrayList<>();
  private final @NotNull List<Integration> integrations = new ArrayList<>();

  private @Nullable String dsn;
  private long shutdownTimeoutMills = 5000;
  private boolean debug;
  private boolean enableNdk = true;
  private @NotNull ILogger logger = NoOpLogger.getInstance();
  private @NotNull SentryLevel diagnosticLevel = DEFAULT_DIAGNOSTIC_LEVEL;
  private @NotNull ISerializer serializer = NoOpSerializer.getInstance();
  private @Nullable String sentryClientName;
  private @Nullable BeforeSendCallback beforeSend;
  private @Nullable BeforeBreadcrumbCallback beforeBreadcrumb;
  private @Nullable String cacheDirPath;
  private int cacheDirSize = 10;
  private int maxBreadcrumbs = 100;
  private @Nullable String release;
  private @Nullable String environment;
  private @Nullable Proxy proxy;
  private @Nullable Double sampleRate;
  private @NotNull List<String> inAppExcludes;
  private @NotNull List<String> inAppIncludes;
  private @Nullable String dist;

  public void addEventProcessor(@NotNull EventProcessor eventProcessor) {
    eventProcessors.add(eventProcessor);
  }

  public @NotNull List<EventProcessor> getEventProcessors() {
    return eventProcessors;
  }

  public void addIntegration(@NotNull Integration integration) {
    integrations.add(integration);
  }

  public @NotNull List<Integration> getIntegrations() {
    return integrations;
  }

  public @Nullable String getDsn() {
    return dsn;
  }

  public void setDsn(@Nullable String dsn) {
    this.dsn = dsn;
  }

  public boolean isDebug() {
    return debug;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  public @NotNull ILogger getLogger() {
    return logger;
  }

  public void setLogger(@Nullable ILogger logger) {
    this.logger = logger == null ? NoOpLogger.getInstance() : new DiagnosticLogger(this, logger);
  }

  public @NotNull SentryLevel getDiagnosticLevel() {
    return diagnosticLevel;
  }

  public void setDiagnosticLevel(@Nullable SentryLevel diagnosticLevel) {
    if (diagnosticLevel == null) {
      diagnosticLevel = DEFAULT_DIAGNOSTIC_LEVEL;
    }
    this.diagnosticLevel = diagnosticLevel;
  }

  public @NotNull ISerializer getSerializer() {
    return serializer;
  }

  public void setSerializer(@Nullable ISerializer serializer) {
    this.serializer = serializer != null ? serializer : NoOpSerializer.getInstance();
  }

  public boolean isEnableNdk() {
    return enableNdk;
  }

  public void setEnableNdk(boolean enableNdk) {
    this.enableNdk = enableNdk;
  }

  public long getShutdownTimeout() {
    return shutdownTimeoutMills;
  }

  public void setShutdownTimeout(long shutdownTimeoutMills) {
    this.shutdownTimeoutMills = shutdownTimeoutMills;
  }

  public @Nullable String getSentryClientName() {
    return sentryClientName;
  }

  public void setSentryClientName(@Nullable String sentryClientName) {
    this.sentryClientName = sentryClientName;
  }

  public @Nullable BeforeSendCallback getBeforeSend() {
    return beforeSend;
  }

  public void setBeforeSend(@Nullable BeforeSendCallback beforeSend) {
    this.beforeSend = beforeSend;
  }

  public @Nullable BeforeBreadcrumbCallback getBeforeBreadcrumb() {
    return beforeBreadcrumb;
  }

  public void setBeforeBreadcrumb(@Nullable BeforeBreadcrumbCallback beforeBreadcrumb) {
    this.beforeBreadcrumb = beforeBreadcrumb;
  }

  public @Nullable String getCacheDirPath() {
    return cacheDirPath;
  }

  public @Nullable String getOutboxPath() {
    if (cacheDirPath == null || cacheDirPath.isEmpty()) {
      return null;
    }
    return cacheDirPath + File.separator + "outbox";
  }

  public void setCacheDirPath(@Nullable String cacheDirPath) {
    this.cacheDirPath = cacheDirPath;
  }

  public int getCacheDirSize() {
    return cacheDirSize;
  }

  public void setCacheDirSize(int cacheDirSize) {
    this.cacheDirSize = cacheDirSize;
  }

  public int getMaxBreadcrumbs() {
    return maxBreadcrumbs;
  }

  public void setMaxBreadcrumbs(int maxBreadcrumbs) {
    this.maxBreadcrumbs = maxBreadcrumbs;
  }

  public @Nullable String getRelease() {
    return release;
  }

  public void setRelease(@Nullable String release) {
    this.release = release;
  }

  public @Nullable String getEnvironment() {
    return environment;
  }

  public void setEnvironment(@Nullable String environment) {
    this.environment = environment;
  }

  public @Nullable Proxy getProxy() {
    return proxy;
  }

  public void setProxy(@Nullable Proxy proxy) {
    this.proxy = proxy;
  }

  public @Nullable Double getSampleRate() {
    return sampleRate;
  }

  // Can be anything between 0.01 (1%) and 1.0 (99.9%) or null (default), to disable it.
  public void setSampleRate(Double sampleRate) {
    if (sampleRate != null && (sampleRate > 1.0 || sampleRate <= 0.0)) {
      throw new IllegalArgumentException(
          "The value "
              + sampleRate
              + " is not valid. Use null to disable or values between 0.01 (inclusive) and 1.0 (exclusive).");
    }
    this.sampleRate = sampleRate;
  }

  public @NotNull List<String> getInAppExcludes() {
    return inAppExcludes;
  }

  public void addInAppExclude(@NotNull String exclude) {
    if (inAppExcludes == null) {
      inAppExcludes = new ArrayList<>();
    }
    inAppExcludes.add(exclude);
  }

  public @NotNull List<String> getInAppIncludes() {
    return inAppIncludes;
  }

  public void addInAppInclude(@NotNull String include) {
    if (inAppIncludes == null) {
      inAppIncludes = new ArrayList<>();
    }
    inAppIncludes.add(include);
  }

  public @Nullable String getDist() {
    return dist;
  }

  public void setDist(@Nullable String dist) {
    this.dist = dist;
  }

  public interface BeforeSendCallback {
    @Nullable
    SentryEvent execute(@NotNull SentryEvent event, @Nullable Object hint);
  }

  public interface BeforeBreadcrumbCallback {
    @Nullable
    Breadcrumb execute(@NotNull Breadcrumb breadcrumb, @Nullable Object hint);
  }

  public SentryOptions() {
    inAppExcludes = new ArrayList<>();
    inAppExcludes.add("io.sentry.");
    inAppExcludes.add("java.");
    inAppExcludes.add("javax.");
    inAppExcludes.add("sun.");
    inAppExcludes.add("com.oracle.");
    inAppExcludes.add("oracle.");
    inAppExcludes.add("org.jetbrains.");

    inAppIncludes =
        new ArrayList<>(); // make the list available so processor below can take the reference

    eventProcessors.add(new MainEventProcessor(this));

    // Start off sending any cached event.
    integrations.add(
        new SendCachedEventFireAndForgetIntegration(
            (hub, options) -> {
              SendCachedEvent sender =
                  new SendCachedEvent(options.getSerializer(), hub, options.getLogger());
              if (options.getCacheDirPath() != null) {
                File cacheDir = new File(options.getCacheDirPath());
                return () -> sender.processDirectory(cacheDir);
              } else {
                logIfNotNull(
                    getLogger(),
                    SentryLevel.WARNING,
                    "No cache dir path is defined in options, discarding SendCachedEvent.");
                return null;
              }
            }));
    // Send cache envelopes from NDK
    integrations.add(
        new SendCachedEventFireAndForgetIntegration(
            (hub, options) -> {
              EnvelopeSender envelopeSender =
                  new EnvelopeSender(
                      hub, new io.sentry.core.EnvelopeReader(), options.getSerializer(), logger);
              if (options.getOutboxPath() != null) {
                File outbox = new File(options.getOutboxPath());
                return () -> envelopeSender.processDirectory(outbox);
              } else {
                logIfNotNull(
                    getLogger(),
                    SentryLevel.WARNING,
                    "No outbox dir path is defined in options, discarding EnvelopeSender.");
                return null;
              }
            }));
    integrations.add(new UncaughtExceptionHandlerIntegration());
  }
}
