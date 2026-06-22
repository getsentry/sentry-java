package io.sentry.android.core.performance;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationStartInfo;
import android.content.ContentProvider;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import io.sentry.IContinuousProfiler;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ITransactionProfiler;
import io.sentry.NoOpLogger;
import io.sentry.SentryDate;
import io.sentry.TracesSamplingDecision;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.android.core.ContextUtils;
import io.sentry.android.core.CurrentActivityHolder;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.android.core.internal.util.FirstDrawDoneListener;
import io.sentry.protocol.SentryId;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.LazyEvaluator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * An in-memory representation for app-metrics during app start. As the SDK can't be initialized
 * that early, we can't use transactions or spans directly. Thus simple TimeSpans are used and later
 * transformed into SDK specific txn/span data structures.
 *
 * <p>This class is also responsible for - determining the app start type (cold, warm) - determining
 * if the app was launched in foreground
 */
@ApiStatus.Internal
public class AppStartMetrics extends ActivityLifecycleCallbacksAdapter {
  public interface HeadlessAppStartListener {
    void onHeadlessAppStart();
  }

  public enum AppStartType {
    UNKNOWN,
    COLD,
    WARM
  }

  private static long CLASS_LOADED_UPTIME_MS = SystemClock.uptimeMillis();

  private static volatile @Nullable AppStartMetrics instance;
  public static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();

  private @NotNull AppStartType appStartType = AppStartType.UNKNOWN;
  private final LazyEvaluator<Boolean> appLaunchedInForeground =
      new LazyEvaluator<>(
          new LazyEvaluator.Evaluator<Boolean>() {
            @Override
            public @NotNull Boolean evaluate() {
              return ContextUtils.isForegroundImportance();
            }
          });
  private volatile long firstIdle = -1;

  private final @NotNull TimeSpan appStartSpan;
  private final @NotNull TimeSpan sdkInitTimeSpan;
  private final @NotNull TimeSpan applicationOnCreate;
  private final @NotNull Map<ContentProvider, TimeSpan> contentProviderOnCreates;
  private final @NotNull List<ActivityLifecycleTimeSpan> activityLifecycles;
  private @Nullable ITransactionProfiler appStartProfiler = null;
  private @Nullable IContinuousProfiler appStartContinuousProfiler = null;
  private @Nullable TracesSamplingDecision appStartSamplingDecision = null;
  private boolean isCallbackRegistered = false;
  private boolean shouldSendStartMeasurements = true;
  private final AtomicInteger activeActivitiesCounter = new AtomicInteger();
  private final AtomicBoolean firstDrawDone = new AtomicBoolean(false);
  private final AtomicBoolean headlessAppStartCheckPending = new AtomicBoolean(false);
  private final AtomicBoolean headlessAppStartListenerInvoked = new AtomicBoolean(false);
  private volatile @Nullable HeadlessAppStartListener headlessAppStartListener;
  // Captures a headless app.start so a later ui.load can share its trace.
  private @Nullable SentryId appStartTraceId;
  private @Nullable String appStartSentryTraceHeader;
  private @Nullable String appStartBaggageHeader;
  private @Nullable SentryDate appStartEndTime;
  private @Nullable ApplicationStartInfo cachedStartInfo;

  public static @NotNull AppStartMetrics getInstance() {
    if (instance == null) {
      try (final @NotNull ISentryLifecycleToken ignored = staticLock.acquire()) {
        if (instance == null) {
          instance = new AppStartMetrics();
        }
      }
    }
    //noinspection DataFlowIssue
    return instance;
  }

  public AppStartMetrics() {
    appStartSpan = new TimeSpan();
    sdkInitTimeSpan = new TimeSpan();
    applicationOnCreate = new TimeSpan();
    contentProviderOnCreates = new HashMap<>();
    activityLifecycles = new ArrayList<>();
  }

  /**
   * @return the app start span Uses Process.getStartUptimeMillis() as start timestamp, which
   *     requires API level 24+
   */
  public @NotNull TimeSpan getAppStartTimeSpan() {
    return appStartSpan;
  }

  /**
   * @return the app start span Uses Process.getStartUptimeMillis() as start timestamp, which
   *     requires API level 24+
   */
  public @NotNull TimeSpan createProcessInitSpan() {
    // AppStartSpan and CLASS_LOADED_UPTIME_MS can be modified at any time.
    // So, we cannot cache the processInitSpan, but we need to create it when needed.
    final @NotNull TimeSpan processInitSpan = new TimeSpan();
    processInitSpan.setup(
        "Process Initialization",
        appStartSpan.getStartTimestampMs(),
        appStartSpan.getStartUptimeMs(),
        CLASS_LOADED_UPTIME_MS);
    return processInitSpan;
  }

  /**
   * @return the SDK init time span, as measured pre-performance-v2 Uses ContentProvider/Sdk init
   *     time as start timestamp
   *     <p>Data is filled by either {@link io.sentry.android.core.SentryPerformanceProvider} with a
   *     fallback to {@link io.sentry.android.core.SentryAndroid}. At least the start timestamp
   *     should always be set.
   */
  public @NotNull TimeSpan getSdkInitTimeSpan() {
    return sdkInitTimeSpan;
  }

  public @NotNull TimeSpan getApplicationOnCreateTimeSpan() {
    return applicationOnCreate;
  }

  public void setAppStartType(final @NotNull AppStartType appStartType) {
    this.appStartType = appStartType;
  }

  public @NotNull AppStartType getAppStartType() {
    return appStartType;
  }

  /**
   * The reason the OS started the process, mapped from {@link ApplicationStartInfo#getReason()}.
   * Only available on API 35+ (when {@link #cachedStartInfo} was resolved); returns {@code null}
   * otherwise or for an unmapped reason.
   */
  public @Nullable String getAppStartReason() {
    if (cachedStartInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      return null;
    }
    switch (cachedStartInfo.getReason()) {
      case ApplicationStartInfo.START_REASON_ALARM:
        return "alarm";
      case ApplicationStartInfo.START_REASON_BACKUP:
        return "backup";
      case ApplicationStartInfo.START_REASON_BOOT_COMPLETE:
        return "boot_complete";
      case ApplicationStartInfo.START_REASON_BROADCAST:
        return "broadcast";
      case ApplicationStartInfo.START_REASON_CONTENT_PROVIDER:
        return "content_provider";
      case ApplicationStartInfo.START_REASON_JOB:
        return "job";
      case ApplicationStartInfo.START_REASON_LAUNCHER:
        return "launcher";
      case ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS:
        return "launcher_recents";
      case ApplicationStartInfo.START_REASON_PUSH:
        return "push";
      case ApplicationStartInfo.START_REASON_SERVICE:
        return "service";
      case ApplicationStartInfo.START_REASON_START_ACTIVITY:
        return "start_activity";
      case ApplicationStartInfo.START_REASON_OTHER:
        return "other";
      default:
        return null;
    }
  }

  public boolean isAppLaunchedInForeground() {
    return appLaunchedInForeground.getValue();
  }

  @VisibleForTesting
  public void setAppLaunchedInForeground(final boolean appLaunchedInForeground) {
    this.appLaunchedInForeground.setValue(appLaunchedInForeground);
  }

  public void setHeadlessAppStartListener(final @Nullable HeadlessAppStartListener listener) {
    this.headlessAppStartListener = listener;
    if (listener != null
        && isCallbackRegistered
        && activeActivitiesCounter.get() == 0
        && !firstDrawDone.get()) {
      scheduleHeadlessAppStartCheckOnMain();
    }
  }

  public @Nullable SentryId getAppStartTraceId() {
    return appStartTraceId;
  }

  public void setAppStartTraceId(final @Nullable SentryId traceId) {
    this.appStartTraceId = traceId;
  }

  public @Nullable String getAppStartSentryTraceHeader() {
    return appStartSentryTraceHeader;
  }

  public void setAppStartSentryTraceHeader(final @Nullable String appStartSentryTraceHeader) {
    this.appStartSentryTraceHeader = appStartSentryTraceHeader;
  }

  public @Nullable String getAppStartBaggageHeader() {
    return appStartBaggageHeader;
  }

  public void setAppStartBaggageHeader(final @Nullable String appStartBaggageHeader) {
    this.appStartBaggageHeader = appStartBaggageHeader;
  }

  public @Nullable SentryDate getAppStartEndTime() {
    return appStartEndTime;
  }

  public void setAppStartEndTime(final @Nullable SentryDate appStartEndTime) {
    this.appStartEndTime = appStartEndTime;
  }

  /**
   * Provides all collected content provider onCreate time spans
   *
   * @return A sorted list of all onCreate calls
   */
  public @NotNull List<TimeSpan> getContentProviderOnCreateTimeSpans() {
    final List<TimeSpan> spans = new ArrayList<>(contentProviderOnCreates.values());
    Collections.sort(spans);
    return spans;
  }

  public @NotNull List<ActivityLifecycleTimeSpan> getActivityLifecycleTimeSpans() {
    final List<ActivityLifecycleTimeSpan> spans = new ArrayList<>(activityLifecycles);
    Collections.sort(spans);
    return spans;
  }

  public void addActivityLifecycleTimeSpans(final @NotNull ActivityLifecycleTimeSpan timeSpan) {
    activityLifecycles.add(timeSpan);
  }

  public void onAppStartSpansSent() {
    shouldSendStartMeasurements = false;
    contentProviderOnCreates.clear();
    activityLifecycles.clear();
  }

  public boolean shouldSendStartMeasurements(final boolean ignoreForegroundCheck) {
    return shouldSendStartMeasurements
        && (ignoreForegroundCheck || appLaunchedInForeground.getValue());
  }

  public boolean shouldSendStartMeasurements() {
    return shouldSendStartMeasurements(false);
  }

  public long getClassLoadedUptimeMs() {
    return CLASS_LOADED_UPTIME_MS;
  }

  /**
   * Returns a valid app start time span, bypassing the foreground check. Tries appStartSpan first,
   * falls back to sdkInitTimeSpan. Used for headless starts where appLaunchedInForeground is false.
   */
  public @NotNull TimeSpan getAppStartTimeSpanForHeadless() {
    if (appStartSpan.hasStarted() && appStartSpan.hasStopped()) {
      return appStartSpan;
    }
    return sdkInitTimeSpan;
  }

  /**
   * @return the app start time span if it was started and perf-2 is enabled, falls back to the sdk
   *     init time span otherwise
   */
  public @NotNull TimeSpan getAppStartTimeSpanWithFallback(
      final @NotNull SentryAndroidOptions options) {
    // If the app start type was never determined or app wasn't launched in foreground,
    // the app start is considered invalid
    if (appStartType != AppStartType.UNKNOWN && appLaunchedInForeground.getValue()) {
      if (options.isEnablePerformanceV2()) {
        // Only started when sdk version is >= N
        final @NotNull TimeSpan appStartSpan = getAppStartTimeSpan();
        if (appStartSpan.hasStarted()
            && appStartSpan.getDurationMs() <= TimeUnit.MINUTES.toMillis(1)) {
          return appStartSpan;
        }
      }

      // fallback: use sdk init time span, as it will always have a start time set
      final @NotNull TimeSpan sdkInitTimeSpan = getSdkInitTimeSpan();
      if (sdkInitTimeSpan.hasStarted()
          && sdkInitTimeSpan.getDurationMs() <= TimeUnit.MINUTES.toMillis(1)) {
        return sdkInitTimeSpan;
      }
    }

    return new TimeSpan();
  }

  @TestOnly
  void setFirstIdle(final long firstIdle) {
    this.firstIdle = firstIdle;
  }

  @TestOnly
  long getFirstIdle() {
    return firstIdle;
  }

  @TestOnly
  public void clear() {
    appStartType = AppStartType.UNKNOWN;
    appStartSpan.reset();
    sdkInitTimeSpan.reset();
    applicationOnCreate.reset();
    contentProviderOnCreates.clear();
    activityLifecycles.clear();
    if (appStartProfiler != null) {
      appStartProfiler.close();
    }
    appStartProfiler = null;
    if (appStartContinuousProfiler != null) {
      appStartContinuousProfiler.close(true);
    }
    appStartContinuousProfiler = null;
    appStartSamplingDecision = null;
    appLaunchedInForeground.resetValue();
    isCallbackRegistered = false;
    shouldSendStartMeasurements = true;
    firstDrawDone.set(false);
    activeActivitiesCounter.set(0);
    firstIdle = -1;
    headlessAppStartCheckPending.set(false);
    headlessAppStartListenerInvoked.set(false);
    headlessAppStartListener = null;
    appStartTraceId = null;
    appStartSentryTraceHeader = null;
    appStartBaggageHeader = null;
    appStartEndTime = null;
    cachedStartInfo = null;
  }

  public @Nullable ITransactionProfiler getAppStartProfiler() {
    return appStartProfiler;
  }

  public void setAppStartProfiler(final @Nullable ITransactionProfiler appStartProfiler) {
    this.appStartProfiler = appStartProfiler;
  }

  public @Nullable IContinuousProfiler getAppStartContinuousProfiler() {
    return appStartContinuousProfiler;
  }

  public void setAppStartContinuousProfiler(
      final @Nullable IContinuousProfiler appStartContinuousProfiler) {
    this.appStartContinuousProfiler = appStartContinuousProfiler;
  }

  public void setAppStartSamplingDecision(
      final @Nullable TracesSamplingDecision appStartSamplingDecision) {
    this.appStartSamplingDecision = appStartSamplingDecision;
  }

  public @Nullable TracesSamplingDecision getAppStartSamplingDecision() {
    return appStartSamplingDecision;
  }

  @TestOnly
  @ApiStatus.Internal
  public void setClassLoadedUptimeMs(final long classLoadedUptimeMs) {
    CLASS_LOADED_UPTIME_MS = classLoadedUptimeMs;
  }

  @TestOnly
  @ApiStatus.Internal
  public void setCachedStartInfo(final @Nullable ApplicationStartInfo cachedStartInfo) {
    this.cachedStartInfo = cachedStartInfo;
  }

  /**
   * Called by instrumentation
   *
   * @param application The application object where onCreate was called on
   * @noinspection unused
   */
  public static void onApplicationCreate(final @NotNull Application application) {
    final long now = SystemClock.uptimeMillis();

    final @NotNull AppStartMetrics instance = getInstance();
    if (instance.applicationOnCreate.hasNotStarted()) {
      instance.applicationOnCreate.setStartedAt(now);
      instance.registerLifecycleCallbacks(application);
    }
  }

  /**
   * Called by instrumentation
   *
   * @param application The application object where onCreate was called on
   * @noinspection unused
   */
  public static void onApplicationPostCreate(final @NotNull Application application) {
    final long now = SystemClock.uptimeMillis();

    final @NotNull AppStartMetrics instance = getInstance();
    if (instance.applicationOnCreate.hasNotStopped()) {
      instance.applicationOnCreate.setDescription(application.getClass().getName() + ".onCreate");
      instance.applicationOnCreate.setStoppedAt(now);
    }
  }

  /**
   * Register a callback to check if an activity was started after the application was created. Must
   * be called from the main thread.
   *
   * @param application The application object to register the callback to
   */
  public void registerLifecycleCallbacks(final @NotNull Application application) {
    if (isCallbackRegistered) {
      return;
    }
    isCallbackRegistered = true;
    appLaunchedInForeground.resetValue();
    application.registerActivityLifecycleCallbacks(instance);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      final @Nullable ActivityManager activityManager =
          (ActivityManager) application.getSystemService(Context.ACTIVITY_SERVICE);
      if (activityManager != null) {
        try {
          final List<ApplicationStartInfo> historicalProcessStartReasons =
              activityManager.getHistoricalProcessStartReasons(1);
          if (!historicalProcessStartReasons.isEmpty()) {
            final @NotNull ApplicationStartInfo info = historicalProcessStartReasons.get(0);
            cachedStartInfo = info;
            if (info.getStartupState() == ApplicationStartInfo.STARTUP_STATE_STARTED) {
              if (info.getStartType() == ApplicationStartInfo.START_TYPE_COLD) {
                appStartType = AppStartType.COLD;
              } else {
                appStartType = AppStartType.WARM;
              }
            }
          }
        } catch (RuntimeException ignored) {
          // getHistoricalProcessStartReasons may throw different kinds of exceptions, namely:
          // - SecurityException when called from an isolated process
          // - IllegalArgumentException when called with a wrong userId
          // - others
          // See impl:
          // https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/services/core/java/com/android/server/am/ActivityManagerService.java;l=10866-10893
          Log.w("AppStartMetrics", ignored); // no logger instance here, so we just Log
        }
      }
    }

    if (appStartType == AppStartType.UNKNOWN || headlessAppStartListener != null) {
      scheduleHeadlessAppStartCheckOnMain();
    }
  }

  private void scheduleHeadlessAppStartCheckOnMain() {
    if (!headlessAppStartCheckPending.compareAndSet(false, true)) {
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Looper.getMainLooper()
          .getQueue()
          .addIdleHandler(
              () -> {
                firstIdle = SystemClock.uptimeMillis();
                headlessAppStartCheckPending.set(false);
                handleHeadlessAppStartIfNeededOnMain();
                return false;
              });
    } else {
      final Handler handler = new Handler(Looper.getMainLooper());
      handler.post(
          () -> {
            firstIdle = SystemClock.uptimeMillis();
            handler.post(
                () -> {
                  headlessAppStartCheckPending.set(false);
                  handleHeadlessAppStartIfNeededOnMain();
                });
          });
    }
  }

  /**
   * Checks whether startup reached an Activity after the main looper had a chance to create one. If
   * not, handles the headless app start path. Must be called on the main thread.
   */
  private void handleHeadlessAppStartIfNeededOnMain() {
    if (activeActivitiesCounter.get() == 0) {
      // SDK init happened after Application.onCreate (e.g. deferred/late init inside an Activity):
      // we missed the Activity's onActivityCreated, but a foreground process means it was a real
      // launch, not a headless start. Gated on the listener so only the standalone-app-start path
      // (which is what could emit a headless transaction) is affected.
      if (headlessAppStartListener != null && ContextUtils.isForegroundImportance()) {
        return;
      }

      appLaunchedInForeground.setValue(false);

      // Headless starts have no Activity signal for the pre-API 35 warm/cold heuristic.
      // If ApplicationStartInfo did not resolve the type, classify the process start as cold.
      if (appStartType == AppStartType.UNKNOWN) {
        appStartType = AppStartType.COLD;
      }

      // we stop the app start profilers, as they are useless and likely to timeout
      if (appStartProfiler != null && appStartProfiler.isRunning()) {
        appStartProfiler.close();
        appStartProfiler = null;
      }
      if (appStartContinuousProfiler != null && appStartContinuousProfiler.isRunning()) {
        appStartContinuousProfiler.close(true);
        appStartContinuousProfiler = null;
      }

      final @Nullable HeadlessAppStartListener listener = headlessAppStartListener;
      if (listener != null && headlessAppStartListenerInvoked.compareAndSet(false, true)) {
        resolveHeadlessAppStartEndTime();
        listener.onHeadlessAppStart();
      }
    }
  }

  private void resolveHeadlessAppStartEndTime() {
    // Priority 1: Gradle plugin instrumented onApplicationPostCreate
    if (applicationOnCreate.hasStopped()) {
      final long stopUptimeMs =
          applicationOnCreate.getStartUptimeMs() + applicationOnCreate.getDurationMs();
      stopHeadlessAppStartAt(stopUptimeMs);
      return;
    }

    // Priority 2: API 35+ ApplicationStartInfo (cached from registerLifecycleCallbacks)
    if (cachedStartInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      try {
        final @NotNull Map<Integer, Long> timestamps = cachedStartInfo.getStartupTimestamps();
        final @Nullable Long onCreateStartNanos =
            timestamps.get(ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE);
        if (onCreateStartNanos != null) {
          // The framework captures this timestamp with SystemClock.uptimeNanos() right *before*
          // invoking Application.onCreate (see ActivityThread.handleBindApplication), so it marks
          // the onCreate start, not its end. Without plugin instrumentation there is no onCreate
          // end signal, so this is the best available lower bound for the app start end time.
          // Same clock base as TimeSpan, so it can be used directly without re-anchoring.
          final long onCreateStartUptimeMs = TimeUnit.NANOSECONDS.toMillis(onCreateStartNanos);
          stopHeadlessAppStartAt(onCreateStartUptimeMs);
          return;
        }
      } catch (Throwable ignored) {
        // Best effort: never let optional startup timestamp enrichment break app startup.
      }
    }

    // Priority 3: Process init end time (CLASS_LOADED_UPTIME_MS)
    stopHeadlessAppStartAt(CLASS_LOADED_UPTIME_MS);
  }

  private void stopHeadlessAppStartAt(final long stopUptimeMs) {
    if (appStartSpan.hasStarted()) {
      if (appStartSpan.hasNotStopped()) {
        appStartSpan.setStoppedAt(stopUptimeMs);
      }
    } else if (sdkInitTimeSpan.hasStarted() && sdkInitTimeSpan.hasNotStopped()) {
      sdkInitTimeSpan.setStoppedAt(stopUptimeMs);
    }
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    final long activityCreatedUptimeMillis = SystemClock.uptimeMillis();
    CurrentActivityHolder.getInstance().setActivity(activity);

    // the first activity determines the app start type
    if (activeActivitiesCounter.incrementAndGet() == 1 && !firstDrawDone.get()) {
      final long nowUptimeMs = SystemClock.uptimeMillis();

      // If the app (process) was launched more than 1 minute ago, consider it a warm start.
      // NOTE: meaningless in standalone app start mode, where a headless start is already its own
      // standalone transaction and therefore cannot be re-classified as warm.
      final long durationSinceAppStartMillis = nowUptimeMs - appStartSpan.getStartUptimeMs();
      if (!appLaunchedInForeground.getValue()
          || durationSinceAppStartMillis > TimeUnit.MINUTES.toMillis(1)) {
        appStartType = AppStartType.WARM;
        shouldSendStartMeasurements = true;
        appStartSpan.reset();
        appStartSpan.setStartedAt(activityCreatedUptimeMillis);
        CLASS_LOADED_UPTIME_MS = activityCreatedUptimeMillis;
        contentProviderOnCreates.clear();
        applicationOnCreate.reset();
      } else if (appStartType == AppStartType.UNKNOWN) {
        // pre API 35 handling
        if (savedInstanceState != null) {
          appStartType = AppStartType.WARM;
        } else if (firstIdle != -1 && activityCreatedUptimeMillis > firstIdle) {
          appStartType = AppStartType.WARM;
        } else {
          appStartType = AppStartType.COLD;
        }
      }
    }
    appLaunchedInForeground.setValue(true);
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
    CurrentActivityHolder.getInstance().setActivity(activity);

    if (firstDrawDone.get()) {
      return;
    }
    if (activity.getWindow() != null) {
      FirstDrawDoneListener.registerForNextDraw(
          activity, () -> onFirstFrameDrawn(), new BuildInfoProvider(NoOpLogger.getInstance()));
    } else {
      new Handler(Looper.getMainLooper()).post(() -> onFirstFrameDrawn());
    }
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {
    CurrentActivityHolder.getInstance().setActivity(activity);
  }

  @Override
  public void onActivityPaused(@NonNull Activity activity) {
    CurrentActivityHolder.getInstance().clearActivity(activity);
  }

  @Override
  public void onActivityStopped(@NonNull Activity activity) {
    CurrentActivityHolder.getInstance().clearActivity(activity);
  }

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {
    CurrentActivityHolder.getInstance().clearActivity(activity);

    int remainingActivities = activeActivitiesCounter.decrementAndGet();
    if (remainingActivities < 0) {
      activeActivitiesCounter.set(0);
      remainingActivities = 0;
    }
    // if the app is moving into background
    // as the next onActivityCreated will treat it as a new warm app start
    if (remainingActivities == 0 && !activity.isChangingConfigurations()) {
      appStartType = AppStartType.WARM;
      appLaunchedInForeground.setValue(true);
      shouldSendStartMeasurements = true;
      firstDrawDone.set(false);
    }
  }

  /**
   * Called by instrumentation
   *
   * @param contentProvider The content provider where onCreate was called on
   * @noinspection unused
   */
  public static void onContentProviderCreate(final @NotNull ContentProvider contentProvider) {
    final long now = SystemClock.uptimeMillis();

    final TimeSpan measurement = new TimeSpan();
    measurement.setStartedAt(now);
    getInstance().contentProviderOnCreates.put(contentProvider, measurement);
  }

  /**
   * Called by instrumentation
   *
   * @param contentProvider The content provider where onCreate was called on
   * @noinspection unused
   */
  public static void onContentProviderPostCreate(final @NotNull ContentProvider contentProvider) {
    final long now = SystemClock.uptimeMillis();

    final @Nullable TimeSpan measurement =
        getInstance().contentProviderOnCreates.get(contentProvider);
    if (measurement != null && measurement.hasNotStopped()) {
      measurement.setDescription(contentProvider.getClass().getName() + ".onCreate");
      measurement.setStoppedAt(now);
    }
  }

  synchronized void onFirstFrameDrawn() {
    if (!firstDrawDone.getAndSet(true)) {
      final @NotNull AppStartMetrics appStartMetrics = getInstance();
      appStartMetrics.getSdkInitTimeSpan().stop();
      appStartMetrics.getAppStartTimeSpan().stop();
    }
  }
}
