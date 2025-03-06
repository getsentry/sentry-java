package io.sentry.android.core.performance;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import io.sentry.ITransactionProfiler;
import io.sentry.NoOpLogger;
import io.sentry.TracesSamplingDecision;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.android.core.ContextUtils;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.android.core.internal.util.FirstDrawDoneListener;
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

  public enum AppStartType {
    UNKNOWN,
    COLD,
    WARM
  }

  private static long CLASS_LOADED_UPTIME_MS = SystemClock.uptimeMillis();

  private static volatile @Nullable AppStartMetrics instance;

  private @NotNull AppStartType appStartType = AppStartType.UNKNOWN;
  private boolean appLaunchedInForeground;

  private final @NotNull TimeSpan appStartSpan;
  private final @NotNull TimeSpan sdkInitTimeSpan;
  private final @NotNull TimeSpan applicationOnCreate;
  private final @NotNull Map<ContentProvider, TimeSpan> contentProviderOnCreates;
  private final @NotNull List<ActivityLifecycleTimeSpan> activityLifecycles;
  private @Nullable ITransactionProfiler appStartProfiler = null;
  private @Nullable TracesSamplingDecision appStartSamplingDecision = null;
  private boolean isCallbackRegistered = false;
  private boolean shouldSendStartMeasurements = true;
  private final AtomicInteger activeActivitiesCounter = new AtomicInteger();
  private final AtomicBoolean firstDrawDone = new AtomicBoolean(false);

  public static @NotNull AppStartMetrics getInstance() {

    if (instance == null) {
      synchronized (AppStartMetrics.class) {
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
    appLaunchedInForeground = ContextUtils.isForegroundImportance();
  }

  /**
   * @return the app start span Uses Process.getStartUptimeMillis() as start timestamp, which
   *     requires API level 24+
   */
  public @NotNull TimeSpan getAppStartTimeSpan() {
    return appStartSpan;
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

  public boolean isAppLaunchedInForeground() {
    return appLaunchedInForeground;
  }

  @VisibleForTesting
  public void setAppLaunchedInForeground(final boolean appLaunchedInForeground) {
    this.appLaunchedInForeground = appLaunchedInForeground;
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

  public boolean shouldSendStartMeasurements() {
    return shouldSendStartMeasurements && appLaunchedInForeground;
  }

  public long getClassLoadedUptimeMs() {
    return CLASS_LOADED_UPTIME_MS;
  }

  /**
   * @return the app start time span if it was started and perf-2 is enabled, falls back to the sdk
   *     init time span otherwise
   */
  public @NotNull TimeSpan getAppStartTimeSpanWithFallback(
      final @NotNull SentryAndroidOptions options) {
    // If the app start type was never determined or app wasn't launched in foreground,
    // the app start is considered invalid
    if (appStartType != AppStartType.UNKNOWN && appLaunchedInForeground) {
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
    appStartSamplingDecision = null;
    appLaunchedInForeground = false;
    isCallbackRegistered = false;
    shouldSendStartMeasurements = true;
    firstDrawDone.set(false);
    activeActivitiesCounter.set(0);
  }

  public @Nullable ITransactionProfiler getAppStartProfiler() {
    return appStartProfiler;
  }

  public void setAppStartProfiler(final @Nullable ITransactionProfiler appStartProfiler) {
    this.appStartProfiler = appStartProfiler;
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
   * Register a callback to check if an activity was started after the application was created
   *
   * @param application The application object to register the callback to
   */
  public void registerLifecycleCallbacks(final @NotNull Application application) {
    if (isCallbackRegistered) {
      return;
    }
    isCallbackRegistered = true;
    appLaunchedInForeground = appLaunchedInForeground || ContextUtils.isForegroundImportance();
    application.registerActivityLifecycleCallbacks(instance);
    // We post on the main thread a task to post a check on the main thread. On Pixel devices
    // (possibly others) the first task posted on the main thread is called before the
    // Activity.onCreate callback. This is a workaround for that, so that the Activity.onCreate
    // callback is called before the application one.
    new Handler(Looper.getMainLooper()).post(() -> checkCreateTimeOnMain());
  }

  private void checkCreateTimeOnMain() {
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              // if no activity has ever been created, app was launched in background
              if (activeActivitiesCounter.get() == 0) {
                appLaunchedInForeground = false;

                // we stop the app start profiler, as it's useless and likely to timeout
                if (appStartProfiler != null && appStartProfiler.isRunning()) {
                  appStartProfiler.close();
                  appStartProfiler = null;
                }
              }
            });
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    final long nowUptimeMs = SystemClock.uptimeMillis();

    // the first activity determines the app start type
    if (activeActivitiesCounter.get() == 1 && !firstDrawDone.get()) {
      // If the app (process) was launched more than 1 minute ago, it's likely wrong
      final long durationSinceAppStartMillis = nowUptimeMs - appStartSpan.getStartUptimeMs();
      if (!appLaunchedInForeground || durationSinceAppStartMillis > TimeUnit.MINUTES.toMillis(1)) {
        appStartType = AppStartType.WARM;

        shouldSendStartMeasurements = true;
        appStartSpan.reset();
        appStartSpan.start();
        appStartSpan.setStartedAt(nowUptimeMs);
        CLASS_LOADED_UPTIME_MS = nowUptimeMs;
      } else {
        appStartType = savedInstanceState == null ? AppStartType.COLD : AppStartType.WARM;
      }
    }
    appLaunchedInForeground = true;
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
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
  public void onActivityDestroyed(@NonNull Activity activity) {
    final int remainingActivities = activeActivitiesCounter.decrementAndGet();
    // if the app is moving into background
    // as the next Activity is considered like a new app start
    if (remainingActivities == 0 && !activity.isChangingConfigurations()) {
      appLaunchedInForeground = false;
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
