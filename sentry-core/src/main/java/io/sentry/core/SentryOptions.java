package io.sentry.core;

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

  private List<EventProcessor> eventProcessors = new ArrayList<>();
  private List<Integration> integrations = new ArrayList<>();

  private String dsn;
  private long shutdownTimeoutMills = 5000;
  private boolean debug;
  private boolean enableNdk = true;
  private @NotNull ILogger logger = NoOpLogger.getInstance();
  private SentryLevel diagnosticLevel = DEFAULT_DIAGNOSTIC_LEVEL;
  private ISerializer serializer;
  private String sentryClientName;
  private BeforeSendCallback beforeSend;
  private BeforeBreadcrumbCallback beforeBreadcrumb;
  private String cacheDirPath;
  private int cacheDirSize = 10;
  private int maxBreadcrumbs = 100;
  private String release;
  private String environment;
  private Proxy proxy;
  private Double sampling;
  private List<String> inAppExcludes;
  private List<String> inAppIncludes;

  public void addEventProcessor(EventProcessor eventProcessor) {
    eventProcessors.add(eventProcessor);
  }

  public List<EventProcessor> getEventProcessors() {
    return eventProcessors;
  }

  public void addIntegration(Integration integration) {
    integrations.add(integration);
  }

  public List<Integration> getIntegrations() {
    return integrations;
  }

  public String getDsn() {
    return dsn;
  }

  public void setDsn(String dsn) {
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

  public SentryLevel getDiagnosticLevel() {
    return diagnosticLevel;
  }

  public void setDiagnosticLevel(SentryLevel diagnosticLevel) {
    if (diagnosticLevel == null) {
      diagnosticLevel = DEFAULT_DIAGNOSTIC_LEVEL;
    }
    this.diagnosticLevel = diagnosticLevel;
  }

  public ISerializer getSerializer() {
    return serializer;
  }

  public void setSerializer(ISerializer serializer) {
    this.serializer = serializer;
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

  public String getSentryClientName() {
    return sentryClientName;
  }

  public void setSentryClientName(String sentryClientName) {
    this.sentryClientName = sentryClientName;
  }

  public BeforeSendCallback getBeforeSend() {
    return beforeSend;
  }

  public void setBeforeSend(BeforeSendCallback beforeSend) {
    this.beforeSend = beforeSend;
  }

  public BeforeBreadcrumbCallback getBeforeBreadcrumb() {
    return beforeBreadcrumb;
  }

  public void setBeforeBreadcrumb(BeforeBreadcrumbCallback beforeBreadcrumb) {
    this.beforeBreadcrumb = beforeBreadcrumb;
  }

  public String getCacheDirPath() {
    return cacheDirPath;
  }

  public String getOutboxPath() {
    return cacheDirPath + File.separator + "outbox";
  }

  public void setCacheDirPath(String cacheDirPath) {
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

  public String getRelease() {
    return release;
  }

  public void setRelease(String release) {
    this.release = release;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public Proxy getProxy() {
    return proxy;
  }

  public void setProxy(Proxy proxy) {
    this.proxy = proxy;
  }

  public Double getSampling() {
    return sampling;
  }

  // Can be anything between 0.01 (1%) and 1.0 (99.9%) or null (default), to disable it.
  public void setSampling(Double sampling) {
    if (sampling != null && (sampling > 1.0 || sampling <= 0.0)) {
      throw new IllegalArgumentException(
          "The value "
              + sampling
              + " is not valid. Use null to disable or values between 0.01 (inclusive) and 1.0 (exclusive).");
    }
    this.sampling = sampling;
  }

  public List<String> getInAppExcludes() {
    return inAppExcludes;
  }

  public void addInAppExclude(String exclude) {
    if (inAppExcludes == null) {
      inAppExcludes = new ArrayList<>();
    }
    inAppExcludes.add(exclude);
  }

  public List<String> getInAppIncludes() {
    return inAppIncludes;
  }

  public void addInAppInclude(String include) {
    if (inAppIncludes == null) {
      inAppIncludes = new ArrayList<>();
    }
    inAppIncludes.add(include);
  }

  public interface BeforeSendCallback {
    SentryEvent execute(SentryEvent event, @Nullable Object hint);
  }

  public interface BeforeBreadcrumbCallback {
    Breadcrumb execute(Breadcrumb breadcrumb, @Nullable Object hint);
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

    eventProcessors.add(new MainEventProcessor(this));
    integrations.add(new UncaughtExceptionHandlerIntegration());
  }
}
