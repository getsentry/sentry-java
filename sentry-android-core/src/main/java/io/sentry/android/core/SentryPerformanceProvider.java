package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import io.sentry.NoOpLogger;
import io.sentry.SentryDate;
import io.sentry.android.core.internal.util.FirstDrawDoneListener;
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

  private final @NotNull BuildInfoProvider buildInfoProvider;

  private final @NotNull MainLooperHandler mainHandler;

  public SentryPerformanceProvider() {
    AppStartState.getInstance().setAppStartTime(appStartMillis, appStartTime);
    buildInfoProvider = new BuildInfoProvider(NoOpLogger.getInstance());
    mainHandler = new MainLooperHandler();
  }

  SentryPerformanceProvider(
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull MainLooperHandler mainHandler) {
    AppStartState.getInstance().setAppStartTime(appStartMillis, appStartTime);
    this.buildInfoProvider = buildInfoProvider;
    this.mainHandler = mainHandler;
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

  @SuppressLint("NewApi")
  @Override
  public void onActivityResumed(@NotNull Activity activity) {
    if (!firstActivityResumed) {
      // sets App start as finished when the very first activity calls onResume
      firstActivityResumed = true;
      final View rootView = activity.findViewById(android.R.id.content);
      if (rootView != null) {
        FirstDrawDoneListener.registerForNextDraw(
            rootView, () -> AppStartState.getInstance().setAppStartEnd(), buildInfoProvider);
      } else {
        // Posting a task to the main thread's handler will make it executed after it finished
        // its current job. That is, right after the activity draws the layout.
        mainHandler.post(() -> AppStartState.getInstance().setAppStartEnd());
      }
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
