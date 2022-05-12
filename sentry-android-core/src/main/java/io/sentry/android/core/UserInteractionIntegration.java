package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.Window;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.gestures.NoOpWindowCallback;
import io.sentry.android.core.internal.gestures.SentryGestureListener;
import io.sentry.android.core.internal.gestures.SentryWindowCallback;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UserInteractionIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;

  private final boolean isAndroidXAvailable;
  private final boolean isAndroidXScrollViewAvailable;

  private final @NotNull ActivityFramesTracker activityFramesTracker;

  public UserInteractionIntegration(
      final @NotNull Application application,
      final @NotNull LoadClass classLoader,
      final @NotNull ActivityFramesTracker activityFramesTracker) {
    this.application = Objects.requireNonNull(application, "Application is required");
    this.activityFramesTracker =
        Objects.requireNonNull(activityFramesTracker, "ActivityFramesTracker is required");

    isAndroidXAvailable =
        classLoader.isClassAvailable("androidx.core.view.GestureDetectorCompat", options);
    isAndroidXScrollViewAvailable =
        classLoader.isClassAvailable("androidx.core.view.ScrollingView", options);
  }

  private void startTracking(final @NotNull Activity activity) {
    final Window window = activity.getWindow();
    if (window == null) {
      if (options != null) {
        options.getLogger().log(SentryLevel.INFO, "Window was null in startTracking");
      }
      return;
    }

    if (hub != null && options != null) {
      Window.Callback delegate = window.getCallback();
      if (delegate == null) {
        delegate = new NoOpWindowCallback();
      }

      final SentryGestureListener gestureListener =
          new SentryGestureListener(
              activity, hub, options, activityFramesTracker, isAndroidXScrollViewAvailable);
      window.setCallback(new SentryWindowCallback(delegate, activity, gestureListener, options));
    }
  }

  private void stopTracking(final @NotNull Activity activity) {
    final Window window = activity.getWindow();
    if (window == null) {
      if (options != null) {
        options.getLogger().log(SentryLevel.INFO, "Window was null in stopTracking");
      }
      return;
    }

    final Window.Callback current = window.getCallback();
    if (current instanceof SentryWindowCallback) {
      ((SentryWindowCallback) current).stopTracking();
      if (((SentryWindowCallback) current).getDelegate() instanceof NoOpWindowCallback) {
        window.setCallback(null);
      } else {
        window.setCallback(((SentryWindowCallback) current).getDelegate());
      }
    }
  }

  @Override
  public void onActivityCreated(@NotNull Activity activity, @Nullable Bundle bundle) {}

  @Override
  public void onActivityStarted(@NotNull Activity activity) {
    // The docs on the screen rendering performance tracing
    // (https://firebase.google.com/docs/perf-mon/screen-traces?platform=android#definition),
    // state that the tracing starts for every Activity class when the app calls .onActivityStarted.
    // Adding an Activity in onActivityCreated leads to Window.FEATURE_NO_TITLE not
    // working. Moving this to onActivityStarted fixes the problem.
    activityFramesTracker.addActivity(activity);
  }

  @Override
  public void onActivityResumed(@NotNull Activity activity) {
    startTracking(activity);
  }

  @Override
  public void onActivityPaused(@NotNull Activity activity) {
    stopTracking(activity);
  }

  @Override
  public void onActivityStopped(@NotNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(@NotNull Activity activity, @NotNull Bundle bundle) {}

  @Override
  public void onActivityDestroyed(@NotNull Activity activity) {}

  @Override
  public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.hub = Objects.requireNonNull(hub, "Hub is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "UserInteractionIntegration enabled: %s",
            this.options.isEnableUserInteractionBreadcrumbs());

    if (this.options.isEnableUserInteractionBreadcrumbs()) {
      if (isAndroidXAvailable) {
        application.registerActivityLifecycleCallbacks(this);
        this.options.getLogger().log(SentryLevel.DEBUG, "UserInteractionIntegration installed.");
      } else {
        options
            .getLogger()
            .log(
                SentryLevel.INFO,
                "androidx.core is not available, UserInteractionIntegration won't be installed");
      }
    }
  }

  @Override
  public void close() throws IOException {
    application.unregisterActivityLifecycleCallbacks(this);

    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "UserInteractionIntegration removed.");
    }

    activityFramesTracker.stop();
  }
}
