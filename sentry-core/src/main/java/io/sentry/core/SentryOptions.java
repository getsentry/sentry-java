package io.sentry.core;

import io.sentry.core.util.NonNull;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

public class SentryOptions {
  static final SentryLevel DEFAULT_DIAGNOSTIC_LEVEL = SentryLevel.DEBUG;

  private List<EventProcessor> eventProcessors = new ArrayList<>();
  private List<Integration> integrations = new ArrayList<>();

  private String dsn;
  private long shutdownTimeoutMills;
  private boolean debug;
  private boolean enableNdk = true;
  private @NonNull ILogger logger = NoOpLogger.getInstance();
  private SentryLevel diagnosticLevel = DEFAULT_DIAGNOSTIC_LEVEL;
  private ISerializer serializer;
  private String sentryClientName;
  private BeforeSendCallback beforeSend;
  private BeforeBreadcrumbCallback beforeBreadcrumb;
  private String cacheDirPath;
  private Proxy proxy;

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

  public @NonNull ILogger getLogger() {
    return logger;
  }

  public void setLogger(ILogger logger) {
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

  public void setCacheDirPath(String cacheDirPath) {
    this.cacheDirPath = cacheDirPath;
  }

  public Proxy getProxy() {
    return proxy;
  }

  public void setProxy(Proxy proxy) {
    this.proxy = proxy;
  }

  public interface BeforeSendCallback {
    SentryEvent execute(SentryEvent event);
  }

  public interface BeforeBreadcrumbCallback {
    Breadcrumb execute(Breadcrumb breadcrumb);
  }

  public SentryOptions() {
    integrations.add(new UncaughtExceptionHandlerIntegration());
  }
}
