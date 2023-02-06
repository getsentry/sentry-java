package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import io.sentry.SentryDate;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * SentryPerformanceProvider is responsible for collecting data (eg appStart) as early as possible
 * as ContentProvider is the only reliable hook for libraries that works across all the supported
 * SDK versions. When minSDK is >= 24, we could use Process.getStartUptimeMillis() We could also use
 * AppComponentFactory but it depends on androidx.core.app.AppComponentFactory
 */
@ApiStatus.Internal
public final class SentryPerformanceProvider extends EmptySecureContentProvider
    implements Application.ActivityLifecycleCallbacks {

  // static to rely on Class load
  private static @NotNull SentryDate appStartTime = AndroidDateUtils.getCurrentSentryDateTime();
  // SystemClock.uptimeMillis() isn't affected by phone provider or clock changes.
  private static long appStartMillis = SystemClock.uptimeMillis();

  private boolean firstActivityCreated = false;
  private boolean firstActivityResumed = false;

  private @Nullable Application application;

  public SentryPerformanceProvider() {
    AppStartState.getInstance().setAppStartTime(appStartMillis, appStartTime);
  }

  @Override
  public boolean onCreate() {
    Context context = getContext();

    if (context == null) {
      return false;
    }

    // it returns null if ContextImpl, so let's check for nullability
    if (context.getApplicationContext() != null) {
      context = context.getApplicationContext();
    }

    if (context instanceof Application) {
      application = ((Application) context);
      application.registerActivityLifecycleCallbacks(this);
    }

    return true;
  }

  @Override
  public void attachInfo(Context context, ProviderInfo info) {
    // applicationId is expected to be prepended. See AndroidManifest.xml
    if (SentryPerformanceProvider.class.getName().equals(info.authority)) {
      throw new IllegalStateException(
          "An applicationId is required to fulfill the manifest placeholder.");
    }
    super.attachInfo(context, info);
  }

  @Nullable
  @Override
  public String getType(@NotNull Uri uri) {
    return null;
  }

  @TestOnly
  static void setAppStartTime(
      final long appStartMillisLong, final @NotNull SentryDate appStartTimeDate) {
    appStartMillis = appStartMillisLong;
    appStartTime = appStartTimeDate;
  }

  @Override
  public void onActivityCreated(@NotNull Activity activity, @Nullable Bundle savedInstanceState) {
    // Hybrid Apps like RN or Flutter init the Android SDK after the MainActivity of the App
    // has been created, and some frameworks overwrites the behaviour of activity lifecycle
    // or it's already too late to get the callback for the very first Activity, hence we
    // register the ActivityLifecycleCallbacks here, since this Provider is always run first.
    if (!firstActivityCreated) {
      // if Activity has savedInstanceState then its a warm start
      // https://developer.android.com/topic/performance/vitals/launch-time#warm
      final boolean coldStart = savedInstanceState == null;
      AppStartState.getInstance().setColdStart(coldStart);

      firstActivityCreated = true;
    }
  }

  @Override
  public void onActivityStarted(@NotNull Activity activity) {}

  @Override
  public void onActivityResumed(@NotNull Activity activity) {
    if (!firstActivityResumed) {
      // we only finish the app start if the process is of foregroundImportance
      firstActivityResumed = true;
      // sets App start as finished when the very first activity calls onResume
      AppStartState.getInstance().setAppStartEnd();
    }
    if (application != null) {
      application.unregisterActivityLifecycleCallbacks(this);
    }
  }

  @Override
  public void onActivityPaused(@NotNull Activity activity) {}

  @Override
  public void onActivityStopped(@NotNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(@NotNull Activity activity, @NotNull Bundle outState) {}

  @Override
  public void onActivityDestroyed(@NotNull Activity activity) {}
}
