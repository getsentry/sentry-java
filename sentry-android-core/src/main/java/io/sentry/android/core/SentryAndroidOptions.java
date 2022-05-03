package io.sentry.android.core;

import io.sentry.ISpan;
import io.sentry.Scope;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import io.sentry.protocol.SdkVersion;
import org.jetbrains.annotations.NotNull;

/** Sentry SDK options for Android */
public final class SentryAndroidOptions extends SentryOptions {

  /**
   * Enable or disable ANR (Application Not Responding) Default is enabled Used by AnrIntegration
   */
  private boolean anrEnabled = true;

  /** ANR Timeout interval in Millis Default is 5000 = 5s Used by AnrIntegration */
  private long anrTimeoutIntervalMillis = 5000;

  /** Enable or disable ANR on Debug mode Default is disabled Used by AnrIntegration */
  private boolean anrReportInDebug = false;

  /**
   * Enable or disable automatic breadcrumbs for Activity lifecycle. Using
   * Application.ActivityLifecycleCallbacks
   */
  private boolean enableActivityLifecycleBreadcrumbs = true;

  /** Enable or disable automatic breadcrumbs for App's lifecycle Using ProcessLifecycleOwner */
  private boolean enableAppLifecycleBreadcrumbs = true;

  /** Enable or disable automatic breadcrumbs for SystemEvents Registering a BroadcastReceiver */
  private boolean enableSystemEventBreadcrumbs = true;

  /** Enable or disable automatic breadcrumbs for App Components Using ComponentCallbacks */
  private boolean enableAppComponentBreadcrumbs = true;

  /** Enable or disable automatic breadcrumbs for User interactions Using Window.Callback */
  private boolean enableUserInteractionBreadcrumbs = true;

  /**
   * Enables the Auto instrumentation for Activity lifecycle tracing.
   *
   * <ul>
   *   <li>It also requires setting {@link SentryOptions#getTracesSampleRate()} or {@link
   *       SentryOptions#getTracesSampler()}.
   * </ul>
   *
   * <ul>
   *   It starts a transaction before each Activity's onCreate method is called
   *   (onActivityPreCreated).
   *   <li>The transaction's name is the Activity's name, e.g. MainActivity.
   *   <li>The transaction's operation is navigation.
   * </ul>
   *
   * <ul>
   *   It finishes the transaction after each activity's onResume method is called
   *   (onActivityPostResumed), this depends on {@link
   *   SentryAndroidOptions#enableActivityLifecycleTracingAutoFinish}.
   *   <li>If {@link SentryAndroidOptions#enableActivityLifecycleTracingAutoFinish} is disabled, you
   *       may finish the transaction manually.
   *   <li>If the transaction is not finished either automatically or manually, we finish it
   *       automatically when each Activity's onDestroy method is called (onActivityDestroyed).
   *   <li>If the previous transaction is not finished when a new Activity is being shown, we finish
   *       it automatically before the new Activity's onCreate is called (onActivityPreCreated).
   *   <li>The transaction status will be {@link SpanStatus#OK} if none is set.
   * </ul>
   *
   * The transaction is automatically bound to the {@link Scope}, but only if there's no transaction
   * already bound to the Scope.
   */
  private boolean enableAutoActivityLifecycleTracing = true;

  /**
   * Enables the Auto instrumentation for Activity lifecycle tracing, but specifically when to
   * finish the transaction, read {@link SentryAndroidOptions#enableAutoActivityLifecycleTracing}.
   *
   * <p>If you require a specific lifecycle to finish a transaction or even after the Activity is
   * fully rendered but still waiting for an IO operation, you could call {@link ISpan#finish()}
   * yourself on {@link Sentry#getSpan()}, be sure that you've finished all of your manually created
   * Spans.
   */
  private boolean enableActivityLifecycleTracingAutoFinish = true;

  /** Interval for profiling traces in milliseconds. Defaults to 100 times per second */
  private int profilingTracesIntervalMillis = 1_000 / 100;

  /** Interface that loads the debug images list */
  private @NotNull IDebugImagesLoader debugImagesLoader = NoOpDebugImagesLoader.getInstance();

  /** Enables or disables the attach screenshot feature when an error happened. */
  private boolean attachScreenshot;

  public SentryAndroidOptions() {
    setSentryClientName(BuildConfig.SENTRY_ANDROID_SDK_NAME + "/" + BuildConfig.VERSION_NAME);
    setSdkVersion(createSdkVersion());
    setAttachServerName(false);

    // enable scope sync for Android by default
    setEnableScopeSync(true);
  }

  private @NotNull SdkVersion createSdkVersion() {
    SdkVersion sdkVersion = getSdkVersion();

    final String name = BuildConfig.SENTRY_ANDROID_SDK_NAME;
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

    sdkVersion.addPackage("maven:io.sentry:sentry-android-core", version);

    return sdkVersion;
  }

  /**
   * Checks if ANR (Application Not Responding) is enabled or disabled Default is enabled
   *
   * @return true if enabled or false otherwise
   */
  public boolean isAnrEnabled() {
    return anrEnabled;
  }

  /**
   * Sets ANR (Application Not Responding) to enabled or disabled Default is enabled
   *
   * @param anrEnabled true for enabled and false for disabled
   */
  public void setAnrEnabled(boolean anrEnabled) {
    this.anrEnabled = anrEnabled;
  }

  /**
   * Returns the ANR timeout internal in Millis Default is 5000 = 5s
   *
   * @return the timeout in millis
   */
  public long getAnrTimeoutIntervalMillis() {
    return anrTimeoutIntervalMillis;
  }

  /**
   * Sets the ANR timeout internal in Millis Default is 5000 = 5s
   *
   * @param anrTimeoutIntervalMillis the timeout internal in Millis
   */
  public void setAnrTimeoutIntervalMillis(long anrTimeoutIntervalMillis) {
    this.anrTimeoutIntervalMillis = anrTimeoutIntervalMillis;
  }

  /**
   * Checks if ANR (Application Not Responding) is enabled or disabled on Debug mode Default is
   * disabled
   *
   * @return true if enabled or false otherwise
   */
  public boolean isAnrReportInDebug() {
    return anrReportInDebug;
  }

  /**
   * Sets ANR (Application Not Responding) to enabled or disabled on Debug mode Default is disabled
   *
   * @param anrReportInDebug true for enabled and false for disabled
   */
  public void setAnrReportInDebug(boolean anrReportInDebug) {
    this.anrReportInDebug = anrReportInDebug;
  }

  public boolean isEnableActivityLifecycleBreadcrumbs() {
    return enableActivityLifecycleBreadcrumbs;
  }

  public void setEnableActivityLifecycleBreadcrumbs(boolean enableActivityLifecycleBreadcrumbs) {
    this.enableActivityLifecycleBreadcrumbs = enableActivityLifecycleBreadcrumbs;
  }

  public boolean isEnableAppLifecycleBreadcrumbs() {
    return enableAppLifecycleBreadcrumbs;
  }

  public void setEnableAppLifecycleBreadcrumbs(boolean enableAppLifecycleBreadcrumbs) {
    this.enableAppLifecycleBreadcrumbs = enableAppLifecycleBreadcrumbs;
  }

  public boolean isEnableSystemEventBreadcrumbs() {
    return enableSystemEventBreadcrumbs;
  }

  public void setEnableSystemEventBreadcrumbs(boolean enableSystemEventBreadcrumbs) {
    this.enableSystemEventBreadcrumbs = enableSystemEventBreadcrumbs;
  }

  public boolean isEnableAppComponentBreadcrumbs() {
    return enableAppComponentBreadcrumbs;
  }

  public void setEnableAppComponentBreadcrumbs(boolean enableAppComponentBreadcrumbs) {
    this.enableAppComponentBreadcrumbs = enableAppComponentBreadcrumbs;
  }

  public boolean isEnableUserInteractionBreadcrumbs() {
    return enableUserInteractionBreadcrumbs;
  }

  public void setEnableUserInteractionBreadcrumbs(boolean enableUserInteractionBreadcrumbs) {
    this.enableUserInteractionBreadcrumbs = enableUserInteractionBreadcrumbs;
  }

  /**
   * Enable or disable all the automatic breadcrumbs
   *
   * @param enable true if enable or false otherwise
   */
  public void enableAllAutoBreadcrumbs(boolean enable) {
    enableActivityLifecycleBreadcrumbs = enable;
    enableAppComponentBreadcrumbs = enable;
    enableSystemEventBreadcrumbs = enable;
    enableAppLifecycleBreadcrumbs = enable;
    enableUserInteractionBreadcrumbs = enable;
  }

  /**
   * Returns the interval for profiling traces in milliseconds.
   *
   * @return the interval for profiling traces in milliseconds.
   */
  public int getProfilingTracesIntervalMillis() {
    return profilingTracesIntervalMillis;
  }

  /**
   * Sets the interval for profiling traces in milliseconds.
   *
   * @param profilingTracesIntervalMillis - the interval for profiling traces in milliseconds.
   */
  public void setProfilingTracesIntervalMillis(final int profilingTracesIntervalMillis) {
    this.profilingTracesIntervalMillis = profilingTracesIntervalMillis;
  }

  /**
   * Returns the Debug image loader
   *
   * @return the image loader
   */
  public @NotNull IDebugImagesLoader getDebugImagesLoader() {
    return debugImagesLoader;
  }

  /**
   * Sets the image loader
   *
   * @param debugImagesLoader the image loader
   */
  public void setDebugImagesLoader(final @NotNull IDebugImagesLoader debugImagesLoader) {
    this.debugImagesLoader =
        debugImagesLoader != null ? debugImagesLoader : NoOpDebugImagesLoader.getInstance();
  }

  public boolean isEnableAutoActivityLifecycleTracing() {
    return enableAutoActivityLifecycleTracing;
  }

  public void setEnableAutoActivityLifecycleTracing(boolean enableAutoActivityLifecycleTracing) {
    this.enableAutoActivityLifecycleTracing = enableAutoActivityLifecycleTracing;
  }

  public boolean isEnableActivityLifecycleTracingAutoFinish() {
    return enableActivityLifecycleTracingAutoFinish;
  }

  public void setEnableActivityLifecycleTracingAutoFinish(
      boolean enableActivityLifecycleTracingAutoFinish) {
    this.enableActivityLifecycleTracingAutoFinish = enableActivityLifecycleTracingAutoFinish;
  }

  public boolean isAttachScreenshot() {
    return attachScreenshot;
  }

  public void setAttachScreenshot(boolean attachScreenshot) {
    this.attachScreenshot = attachScreenshot;
  }
}
