package io.sentry.android.core;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import io.sentry.Hint;
import io.sentry.IScope;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryFeedbackOptions;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import io.sentry.android.core.internal.util.RootChecker;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
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
   *   <li>It also requires setting any of {@link SentryOptions#getTracesSampleRate()} or {@link
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
   * <p>The transaction is automatically bound to the {@link IScope}, but only if there's no
   * transaction already bound to the Scope.
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

  /** Interface that loads the debug images list */
  private @NotNull IDebugImagesLoader debugImagesLoader = NoOpDebugImagesLoader.getInstance();

  /**
   * Enables or disables the attach screenshot feature when an error happened. Use {@link
   * SentryAndroidOptions#setBeforeScreenshotCaptureCallback(BeforeCaptureCallback)} ()} to control
   * when a screenshot should be captured.
   */
  private boolean attachScreenshot;

  /**
   * Enables or disables the attach view hierarchy feature when an error happened. Use {@link
   * SentryAndroidOptions#setBeforeViewHierarchyCaptureCallback(BeforeCaptureCallback)} ()} to
   * control when a view hierarchy should be captured.
   */
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

  /**
   * Controls whether to enable the {@link RootChecker}, which can potentially make apps to be
   * flagged by some app stores as harmful.
   */
  private boolean enableRootCheck = true;

  private @Nullable BeforeCaptureCallback beforeScreenshotCaptureCallback;

  private @Nullable BeforeCaptureCallback beforeViewHierarchyCaptureCallback;

  /** Turns NDK on or off. Default is enabled. */
  private boolean enableNdk = true;

  @NotNull
  private NdkHandlerStrategy ndkHandlerStrategy =
      NdkHandlerStrategy.SENTRY_HANDLER_STRATEGY_DEFAULT;

  /**
   * Enable the Java to NDK Scope sync. The default value for sentry-java is disabled and enabled
   * for sentry-android.
   */
  private boolean enableScopeSync = true;

  /**
   * Whether to enable automatic trace ID generation. This is mainly used by the Hybrid SDKs to
   * control the trace ID generation from the outside.
   */
  private boolean enableAutoTraceIdGeneration = true;

  /** Enable or disable intent extras reporting for system event breadcrumbs. Default is false. */
  private boolean enableSystemEventBreadcrumbsExtras = false;

  public interface BeforeCaptureCallback {

    /**
     * A callback which can be used to suppress capturing of screenshots or view hierarchies. This
     * gives more fine grained control when capturing should be performed. E.g. - only capture
     * screenshots for fatal events - overrule any debouncing for important events <br>
     * As capturing can be resource-intensive, the debounce parameter should be respected if
     * possible.
     *
     * <pre>
     *  if (debounce) {
     *    return false;
     *  } else {
     *    // check event and hint
     *  }
     *  </pre>
     *
     * @param event the event
     * @param hint the hints
     * @param debounce true if capturing is marked for being debounced
     * @return true if capturing should be performed, false otherwise
     */
    boolean execute(@NotNull SentryEvent event, @NotNull Hint hint, boolean debounce);
  }

  /**
   * Controls whether to report historical ANRs (v2) from the {@link ApplicationExitInfo} system
   * API. When enabled, reports all of the ANRs available in the {@link
   * ActivityManager#getHistoricalProcessExitReasons(String, int, int)} list, as opposed to
   * reporting only the latest one.
   *
   * <p>These events do not affect ANR rate nor are they enriched with additional information from
   * {@link IScope} like breadcrumbs. The events are reported with 'HistoricalAppExitInfo' {@link
   * Mechanism}.
   */
  private boolean reportHistoricalAnrs = false;

  /**
   * Controls whether to report historical Tombstones from the {@link ApplicationExitInfo} system
   * API. When enabled, reports all of the Tombstones available in the {@link
   * ActivityManager#getHistoricalProcessExitReasons(String, int, int)} list, as opposed to
   * reporting only the latest one.
   *
   * <p>These events do not affect crash rate nor are they enriched with additional information from
   * {@link IScope} like breadcrumbs.
   */
  private boolean reportHistoricalTombstones = false;

  /**
   * Controls whether to send ANR (v2) thread dump as an attachment with plain text. The thread dump
   * is being attached from {@link ApplicationExitInfo#getTraceInputStream()}, if available.
   */
  private boolean attachAnrThreadDump = false;

  private boolean enablePerformanceV2 = true;

  private @Nullable SentryFrameMetricsCollector frameMetricsCollector;

  private boolean enableTombstone = false;

  public SentryAndroidOptions() {
    setSentryClientName(BuildConfig.SENTRY_ANDROID_SDK_NAME + "/" + BuildConfig.VERSION_NAME);
    setSdkVersion(createSdkVersion());
    setAttachServerName(false);
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

  /**
   * Sets Tombstone reporting (ApplicationExitInfo.REASON_CRASH_NATIVE) to enabled or disabled.
   *
   * @param enableTombstone true for enabled and false for disabled
   */
  @ApiStatus.Internal
  public void setTombstoneEnabled(boolean enableTombstone) {
    this.enableTombstone = enableTombstone;
  }

  /**
   * Checks if Tombstone reporting (ApplicationExitInfo.REASON_CRASH_NATIVE) is enabled or disabled
   * Default is disabled
   *
   * @return true if enabled or false otherwise
   */
  @ApiStatus.Internal
  public boolean isTombstoneEnabled() {
    return enableTombstone;
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

  @ApiStatus.Internal
  public void setNativeHandlerStrategy(final @NotNull NdkHandlerStrategy ndkHandlerStrategy) {
    this.ndkHandlerStrategy = ndkHandlerStrategy;
  }

  @ApiStatus.Internal
  public int getNdkHandlerStrategy() {
    return ndkHandlerStrategy.getValue();
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

  public boolean isEnableRootCheck() {
    return enableRootCheck;
  }

  public void setEnableRootCheck(final boolean enableRootCheck) {
    this.enableRootCheck = enableRootCheck;
  }

  public @Nullable BeforeCaptureCallback getBeforeScreenshotCaptureCallback() {
    return beforeScreenshotCaptureCallback;
  }

  /**
   * Sets a callback which is executed before capturing screenshots. Only relevant if
   * attachScreenshot is set to true.
   *
   * @param beforeScreenshotCaptureCallback the callback to execute
   */
  public void setBeforeScreenshotCaptureCallback(
      final @NotNull BeforeCaptureCallback beforeScreenshotCaptureCallback) {
    this.beforeScreenshotCaptureCallback = beforeScreenshotCaptureCallback;
  }

  public @Nullable BeforeCaptureCallback getBeforeViewHierarchyCaptureCallback() {
    return beforeViewHierarchyCaptureCallback;
  }

  /**
   * Sets a callback which is executed before capturing view hierarchies. Only relevant if
   * attachViewHierarchy is set to true.
   *
   * @param beforeViewHierarchyCaptureCallback the callback to execute
   */
  public void setBeforeViewHierarchyCaptureCallback(
      final @NotNull BeforeCaptureCallback beforeViewHierarchyCaptureCallback) {
    this.beforeViewHierarchyCaptureCallback = beforeViewHierarchyCaptureCallback;
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

  public boolean isReportHistoricalAnrs() {
    return reportHistoricalAnrs;
  }

  public void setReportHistoricalAnrs(final boolean reportHistoricalAnrs) {
    this.reportHistoricalAnrs = reportHistoricalAnrs;
  }

  @ApiStatus.Internal
  public boolean isReportHistoricalTombstones() {
    return reportHistoricalTombstones;
  }

  @ApiStatus.Internal
  public void setReportHistoricalTombstones(final boolean reportHistoricalTombstones) {
    this.reportHistoricalTombstones = reportHistoricalTombstones;
  }

  public boolean isAttachAnrThreadDump() {
    return attachAnrThreadDump;
  }

  public void setAttachAnrThreadDump(final boolean attachAnrThreadDump) {
    this.attachAnrThreadDump = attachAnrThreadDump;
  }

  /**
   * @return true if performance-v2 is enabled. See {@link #setEnablePerformanceV2(boolean)} for
   *     more details.
   */
  public boolean isEnablePerformanceV2() {
    return enablePerformanceV2;
  }

  /**
   * Enables or disables the Performance V2 SDK features.
   *
   * <p>With this change - Cold app start spans will provide more accurate timings - Cold app start
   * spans will be enriched with detailed ContentProvider, Application and Activity startup times
   *
   * @param enablePerformanceV2 true if enabled or false otherwise
   */
  public void setEnablePerformanceV2(final boolean enablePerformanceV2) {
    this.enablePerformanceV2 = enablePerformanceV2;
  }

  @ApiStatus.Internal
  public @Nullable SentryFrameMetricsCollector getFrameMetricsCollector() {
    return frameMetricsCollector;
  }

  @ApiStatus.Internal
  public void setFrameMetricsCollector(
      final @Nullable SentryFrameMetricsCollector frameMetricsCollector) {
    this.frameMetricsCollector = frameMetricsCollector;
  }

  public boolean isEnableAutoTraceIdGeneration() {
    return enableAutoTraceIdGeneration;
  }

  public void setEnableAutoTraceIdGeneration(final boolean enableAutoTraceIdGeneration) {
    this.enableAutoTraceIdGeneration = enableAutoTraceIdGeneration;
  }

  public boolean isEnableSystemEventBreadcrumbsExtras() {
    return enableSystemEventBreadcrumbsExtras;
  }

  public void setEnableSystemEventBreadcrumbsExtras(
      final boolean enableSystemEventBreadcrumbsExtras) {
    this.enableSystemEventBreadcrumbsExtras = enableSystemEventBreadcrumbsExtras;
  }

  static class AndroidUserFeedbackIDialogHandler implements SentryFeedbackOptions.IDialogHandler {
    @Override
    public void showDialog(
        final @Nullable SentryId associatedEventId,
        final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator) {
      final @Nullable Activity activity = CurrentActivityHolder.getInstance().getActivity();
      if (activity == null) {
        Sentry.getCurrentScopes()
            .getOptions()
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "Cannot show user feedback dialog, no activity is available. "
                    + "Make sure to call SentryAndroid.init() in your Application.onCreate() method.");
        return;
      }

      new SentryUserFeedbackDialog.Builder(activity)
          .associatedEventId(associatedEventId)
          .configurator(configurator)
          .create()
          .show();
    }
  }
}
