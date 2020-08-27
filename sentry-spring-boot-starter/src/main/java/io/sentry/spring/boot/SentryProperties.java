package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for Sentry integration. */
@ConfigurationProperties("sentry")
@Open
public class SentryProperties {

  /** Whether Sentry integration should be enabled. */
  private boolean enabled = true;

  /**
   * The DSN tells the SDK where to send the events to. If this value is not provided, the SDK will
   * just not send any events.
   */
  private String dsn = "";

  /**
   * Controls how many seconds to wait before shutting down. Sentry SDKs send events from a
   * background queue and this queue is given a certain amount to drain pending events.
   */
  private Long shutdownTimeoutMillis;

  /**
   * Controls how many seconds to wait before flushing down. Sentry SDKs cache events from a
   * background queue and this queue is given a certain amount to drain pending events.
   */
  private Long flushTimeoutMillis;

  /** Read timeout in milliseconds. */
  private Integer readTimeoutMillis;

  /** Whether to ignore TLS errors. */
  private Boolean bypassSecurity;

  /**
   * Turns debug mode on or off. If debug is enabled SDK will attempt to print out useful debugging
   * information if something goes wrong. Default is disabled.
   */
  private Boolean debug;

  /** minimum LogLevel to be used if debug is enabled */
  private SentryLevel diagnosticLevel = SentryLevel.DEBUG;

  /** This variable controls the total amount of breadcrumbs that should be captured. */
  private Integer maxBreadcrumbs;

  /** Sets the release. SDK will try to automatically configure a release out of the box */
  private String release;

  /**
   * Sets the environment. This string is freeform and not set by default. A release can be
   * associated with more than one environment to separate them in the UI Think staging vs prod or
   * similar.
   */
  private String environment;

  /**
   * Configures the sample rate as a percentage of events to be sent in the range of 0.0 to 1.0. if
   * 1.0 is set it means that 100% of events are sent. If set to 0.1 only 10% of events will be
   * sent. Events are picked randomly.
   */
  private Double sampleRate;

  /**
   * A list of string prefixes of module names that do not belong to the app, but rather third-party
   * packages. Modules considered not to be part of the app will be hidden from stack traces by
   * default.
   */
  private List<String> inAppExcludes = new ArrayList<>();

  /**
   * A list of string prefixes of module names that belong to the app. This option takes precedence
   * over inAppExcludes.
   */
  private List<String> inAppIncludes = new ArrayList<>();

  /** Sets the distribution. Think about it together with release and environment */
  private String dist;

  /** When enabled, threads are automatically attached to all logged events. */
  private Boolean attachThreads;

  /**
   * When enabled, stack traces are automatically attached to all threads logged. Stack traces are
   * always attached to exceptions but when this is set stack traces are also sent with threads
   */
  private Boolean attachStacktrace;

  /** The server name used in the Sentry messages. */
  private String serverName;

  /** Weather to use Git commit id as a release. */
  private boolean useGitCommitIdAsRelease = true;

  /**
   * Applies configuration from this instance to the {@link SentryOptions} instance.
   *
   * @param options the instance of {@link SentryOptions} to apply the configuration to
   */
  public void applyTo(SentryOptions options) {
    options.setDsn(this.getDsn());
    Optional.ofNullable(maxBreadcrumbs).ifPresent(options::setMaxBreadcrumbs);
    Optional.ofNullable(environment).ifPresent(options::setEnvironment);
    Optional.ofNullable(shutdownTimeoutMillis).ifPresent(options::setShutdownTimeout);
    Optional.ofNullable(flushTimeoutMillis).ifPresent(options::setFlushTimeoutMillis);
    Optional.ofNullable(readTimeoutMillis).ifPresent(options::setReadTimeoutMillis);
    Optional.ofNullable(sampleRate).ifPresent(options::setSampleRate);
    Optional.ofNullable(bypassSecurity).ifPresent(options::setBypassSecurity);
    Optional.ofNullable(debug).ifPresent(options::setDebug);
    Optional.ofNullable(attachThreads).ifPresent(options::setAttachThreads);
    Optional.ofNullable(attachStacktrace).ifPresent(options::setAttachStacktrace);
    Optional.ofNullable(diagnosticLevel).ifPresent(options::setDiagnosticLevel);
    Optional.ofNullable(dist).ifPresent(options::setDist);
    Optional.ofNullable(release).ifPresent(options::setRelease);
    Optional.ofNullable(sampleRate).ifPresent(options::setSampleRate);
    Optional.ofNullable(serverName).ifPresent(options::setServerName);
    Optional.ofNullable(inAppExcludes)
        .ifPresent(excludes -> excludes.forEach(options::addInAppExclude));
    Optional.ofNullable(inAppIncludes)
        .ifPresent(includes -> includes.forEach(options::addInAppInclude));
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getDsn() {
    return dsn;
  }

  public void setDsn(String dsn) {
    this.dsn = dsn;
  }

  public long getShutdownTimeoutMillis() {
    return shutdownTimeoutMillis;
  }

  public void setShutdownTimeoutMillis(long shutdownTimeoutMillis) {
    this.shutdownTimeoutMillis = shutdownTimeoutMillis;
  }

  public boolean isDebug() {
    return debug;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  public SentryLevel getDiagnosticLevel() {
    return diagnosticLevel;
  }

  public void setDiagnosticLevel(SentryLevel diagnosticLevel) {
    this.diagnosticLevel = diagnosticLevel;
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

  public Double getSampleRate() {
    return sampleRate;
  }

  public void setSampleRate(Double sampleRate) {
    this.sampleRate = sampleRate;
  }

  public List<String> getInAppExcludes() {
    return inAppExcludes;
  }

  public void setInAppExcludes(List<String> inAppExcludes) {
    this.inAppExcludes = inAppExcludes;
  }

  public List<String> getInAppIncludes() {
    return inAppIncludes;
  }

  public void setInAppIncludes(List<String> inAppIncludes) {
    this.inAppIncludes = inAppIncludes;
  }

  public String getDist() {
    return dist;
  }

  public void setDist(String dist) {
    this.dist = dist;
  }

  public boolean isAttachThreads() {
    return attachThreads;
  }

  public void setAttachThreads(boolean attachThreads) {
    this.attachThreads = attachThreads;
  }

  public boolean isAttachStacktrace() {
    return attachStacktrace;
  }

  public void setAttachStacktrace(boolean attachStacktrace) {
    this.attachStacktrace = attachStacktrace;
  }

  public String getServerName() {
    return serverName;
  }

  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  public void setShutdownTimeoutMillis(Long shutdownTimeoutMillis) {
    this.shutdownTimeoutMillis = shutdownTimeoutMillis;
  }

  public Long getFlushTimeoutMillis() {
    return flushTimeoutMillis;
  }

  public void setFlushTimeoutMillis(Long flushTimeoutMillis) {
    this.flushTimeoutMillis = flushTimeoutMillis;
  }

  public Integer getReadTimeoutMillis() {
    return readTimeoutMillis;
  }

  public void setReadTimeoutMillis(Integer readTimeoutMillis) {
    this.readTimeoutMillis = readTimeoutMillis;
  }

  public Boolean getBypassSecurity() {
    return bypassSecurity;
  }

  public void setBypassSecurity(Boolean bypassSecurity) {
    this.bypassSecurity = bypassSecurity;
  }

  public Boolean getDebug() {
    return debug;
  }

  public void setDebug(Boolean debug) {
    this.debug = debug;
  }

  public void setMaxBreadcrumbs(Integer maxBreadcrumbs) {
    this.maxBreadcrumbs = maxBreadcrumbs;
  }

  public Boolean getAttachThreads() {
    return attachThreads;
  }

  public void setAttachThreads(Boolean attachThreads) {
    this.attachThreads = attachThreads;
  }

  public Boolean getAttachStacktrace() {
    return attachStacktrace;
  }

  public void setAttachStacktrace(Boolean attachStacktrace) {
    this.attachStacktrace = attachStacktrace;
  }

  public boolean isUseGitCommitIdAsRelease() {
    return useGitCommitIdAsRelease;
  }

  public void setUseGitCommitIdAsRelease(boolean useGitCommitIdAsRelease) {
    this.useGitCommitIdAsRelease = useGitCommitIdAsRelease;
  }
}
