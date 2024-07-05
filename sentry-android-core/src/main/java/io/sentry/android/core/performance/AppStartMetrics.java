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
import io.sentry.SentryDate;
import io.sentry.SentryNanotimeDate;
import io.sentry.TracesSamplingDecision;
import io.sentry.android.core.ContextUtils;
import io.sentry.android.core.SentryAndroidOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * An in-memory representation for app-metrics during app start. As the SDK can't be initialized
 * that early, we can't use transactions or spans directly. Thus simple TimeSpans are used and later
 * transformed into SDK specific txn/span data structures.
 */
@ApiStatus.Internal
public class AppStartMetrics implements Application.ActivityLifecycleCallbacks {

  public enum AppStartType {
    UNKNOWN,
    COLD,
    WARM
  }

  private static long CLASS_LOADED_UPTIME_MS = SystemClock.uptimeMillis();

  private static volatile @Nullable AppStartMetrics instance;

  private @NotNull AppStartType appStartType = AppStartType.UNKNOWN;
  private boolean appLaunchedInForeground = false;

  private final @NotNull TimeSpan appStartSpan;
  private final @NotNull TimeSpan sdkInitTimeSpan;
  private final @NotNull TimeSpan applicationOnCreate;
  private final @NotNull Map<ContentProvider, TimeSpan> contentProviderOnCreates;
  private final @NotNull List<ActivityLifecycleTimeSpan> activityLifecycles;
  private @Nullable ITransactionProfiler appStartProfiler = null;
  private @Nullable TracesSamplingDecision appStartSamplingDecision = null;
  private @Nullable SentryDate onCreateTime = null;
  private boolean appLaunchTooLong = false;

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
    final List<TimeSpan> measurements = new ArrayList<>(contentProviderOnCreates.values());
    Collections.sort(measurements);
    return measurements;
  }

  public @NotNull List<ActivityLifecycleTimeSpan> getActivityLifecycleTimeSpans() {
    final List<ActivityLifecycleTimeSpan> measurements = new ArrayList<>(activityLifecycles);
    Collections.sort(measurements);
    return measurements;
  }

  public void addActivityLifecycleTimeSpans(final @NotNull ActivityLifecycleTimeSpan timeSpan) {
    activityLifecycles.add(timeSpan);
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
    if (options.isEnablePerformanceV2()) {
      // Only started when sdk version is >= N
      final @NotNull TimeSpan appStartSpan = getAppStartTimeSpan();
      if (appStartSpan.hasStarted()) {
        return validateAppStartSpan(appStartSpan);
      }
    }

    // fallback: use sdk init time span, as it will always have a start time set
    return validateAppStartSpan(getSdkInitTimeSpan());
  }

  private @NotNull TimeSpan validateAppStartSpan(final @NotNull TimeSpan appStartSpan) {
    // If the app launch took too long or it was launched in the background we return an empty span
    if (appLaunchTooLong || !appLaunchedInForeground) {
      return new TimeSpan();
    }
    return appStartSpan;
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
    appLaunchTooLong = false;
    appLaunchedInForeground = false;
    onCreateTime = null;
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
      application.registerActivityLifecycleCallbacks(instance);
      instance.appLaunchedInForeground = ContextUtils.isForegroundImportance();
      new Handler(Looper.getMainLooper())
          .post(
              () -> {
                // if no activity has ever been created, app was launched in background
                if (instance.onCreateTime == null) {
                  instance.appLaunchedInForeground = false;
                }
              });
    }
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    // An activity already called onCreate()
    if (!appLaunchedInForeground || onCreateTime != null) {
      return;
    }
    onCreateTime = new SentryNanotimeDate();

    final long spanStartMillis = appStartSpan.getStartTimestampMs();
    final long spanEndMillis =
        appStartSpan.hasStopped()
            ? appStartSpan.getProjectedStopTimestampMs()
            : System.currentTimeMillis();
    final long durationMillis = spanEndMillis - spanStartMillis;
    // If the app was launched more than 1 minute ago, it's likely wrong
    if (durationMillis > TimeUnit.MINUTES.toMillis(1)) {
      appLaunchTooLong = true;
    }
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {}

  @Override
  public void onActivityResumed(@NonNull Activity activity) {}

  @Override
  public void onActivityPaused(@NonNull Activity activity) {}

  @Override
  public void onActivityStopped(@NonNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {}

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
}
