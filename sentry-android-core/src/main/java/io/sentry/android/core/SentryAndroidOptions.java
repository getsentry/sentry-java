package io.sentry.android.core;

import io.sentry.ISpan;
import io.sentry.Scope;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import io.sentry.protocol.SdkVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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

  /** Enable or disable automatic breadcrumbs for Network Events Using NetworkCallback */
  private boolean enableNetworkEventBreadcrumbs = true;

  /**
   * Enables the Auto instrumentation for Activity lifecycle tracing.
   *
   * <ul>
   *   <li>It also requires setting any of {@link SentryOptions#getEnableTracing()}, {@link
   *       SentryOptions#getTracesSampleRate()} or {@link SentryOptions#getTracesSampler()}.
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

  /**
   * Profiling traces rate. 101 hz means 101 traces in 1 second. Defaults to 101 to avoid possible
   * lockstep sampling. More on
   * https://stackoverflow.com/questions/45470758/what-is-lockstep-sampling
   */
  private int profilingTracesHz = 101;

  /** Interface that loads the debug images list */
  private @NotNull IDebugImagesLoader debugImagesLoader = NoOpDebugImagesLoader.getInstance();

  /** Enables or disables the attach screenshot feature when an error happened. */
  private boolean attachScreenshot;

  /** Enables or disables the attach view hierarchy feature when an error happened. */
  private boolean attachViewHierarchy;

  /**
   * Enables or disables collecting of device information which requires Inter-Process Communication
   * (IPC)
   */
  private boolean collectAdditionalContext = true;

  /**
   * Controls how many seconds to wait for sending events in case there were Startup Crashes in the
   * previous run. Sentry SDKs normally send events from a background queue, but in the case of
   * Startup Crashes, it blocks the execution of the {@link Sentry#init()} function for the amount
   * of startupCrashFlushTimeoutMillis to make sure the events make it to Sentry.
   *
   * <p>When the timeout is reached, the execution will continue on background.
   *
   * <p>Default is 5000 = 5s.
   */
  private long startupCrashFlushTimeoutMillis = 5000; // 5s

  /**
   * Controls the threshold after the application startup time, within which a crash should happen
   * to be considered a Startup Crash.
   *
   * <p>Startup Crashes are sent on {@link Sentry#init()} in a blocking way, controlled by {@link
   * SentryAndroidOptions#startupCrashFlushTimeoutMillis}.
   *
   * <p>Default is 2000 = 2s.
   */
  private final long startupCrashDurationThresholdMillis = 2000; // 2s

  private boolean enableFramesTracking = true;

  private @Nullable String nativeSdkName = null;

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

  public boolean isEnableNetworkEventBreadcrumbs() {
    return enableNetworkEventBreadcrumbs;
  }

  public void setEnableNetworkEventBreadcrumbs(boolean enableNetworkEventBreadcrumbs) {
    this.enableNetworkEventBreadcrumbs = enableNetworkEventBreadcrumbs;
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
    enableNetworkEventBreadcrumbs = enable;
    setEnableUserInteractionBreadcrumbs(enable);
  }

  /**
   * Returns the interval for profiling traces in milliseconds.
   *
   * @return the interval for profiling traces in milliseconds.
   * @deprecated has no effect and will be removed in future versions. It now just returns 0.
   */
  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public int getProfilingTracesIntervalMillis() {
    return 0;
  }

  /**
   * Sets the interval for profiling traces in milliseconds.
   *
   * @param profilingTracesIntervalMillis - the interval for profiling traces in milliseconds.
   * @deprecated has no effect and will be removed in future versions.
   */
  @Deprecated
  public void setProfilingTracesIntervalMillis(final int profilingTracesIntervalMillis) {}

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

  public boolean isAttachViewHierarchy() {
    return attachViewHierarchy;
  }

  public void setAttachViewHierarchy(boolean attachViewHierarchy) {
    this.attachViewHierarchy = attachViewHierarchy;
  }

  public boolean isCollectAdditionalContext() {
    return collectAdditionalContext;
  }

  public void setCollectAdditionalContext(boolean collectAdditionalContext) {
    this.collectAdditionalContext = collectAdditionalContext;
  }

  public boolean isEnableFramesTracking() {
    return enableFramesTracking;
  }

  /**
   * Enable or disable Frames Tracking, which is used to report slow and frozen frames.
   *
   * @param enableFramesTracking true if frames tracking should be enabled, false otherwise.
   */
  public void setEnableFramesTracking(boolean enableFramesTracking) {
    this.enableFramesTracking = enableFramesTracking;
  }

  /**
   * Returns the Startup Crash flush timeout in Millis
   *
   * @return the timeout in Millis
   */
  @ApiStatus.Internal
  long getStartupCrashFlushTimeoutMillis() {
    return startupCrashFlushTimeoutMillis;
  }

  /**
   * Sets the Startup Crash flush timeout in Millis
   *
   * @param startupCrashFlushTimeoutMillis the timeout in Millis
   */
  @TestOnly
  void setStartupCrashFlushTimeoutMillis(long startupCrashFlushTimeoutMillis) {
    this.startupCrashFlushTimeoutMillis = startupCrashFlushTimeoutMillis;
  }

  /**
   * Returns the Startup Crash duration threshold in Millis
   *
   * @return the threshold in Millis
   */
  @ApiStatus.Internal
  public long getStartupCrashDurationThresholdMillis() {
    return startupCrashDurationThresholdMillis;
  }

  /**
   * Sets the sdk name for the sentry-native ndk module. The value is used for the event->sdk
   * attribute and the sentry_client auth header.
   *
   * @param nativeSdkName the native sdk name
   */
  @ApiStatus.Internal
  public void setNativeSdkName(final @Nullable String nativeSdkName) {
    this.nativeSdkName = nativeSdkName;
  }

  /**
   * Returns the sdk name for the sentry native ndk module.
   *
   * @return the custom SDK name if set, otherwise null
   */
  @ApiStatus.Internal
  public @Nullable String getNativeSdkName() {
    return nativeSdkName;
  }
}
