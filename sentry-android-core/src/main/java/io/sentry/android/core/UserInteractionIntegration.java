package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
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
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UserInteractionIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;

  private final boolean isAndroidXAvailable;
  private final boolean isAndroidXScrollViewAvailable;

  public UserInteractionIntegration(
      final @NotNull Application application, final @NotNull LoadClass classLoader) {
    this.application = Objects.requireNonNull(application, "Application is required");

    isAndroidXAvailable =
        classLoader.isClassAvailable("androidx.core.view.GestureDetectorCompat", options);
    isAndroidXScrollViewAvailable =
        classLoader.isClassAvailable("androidx.core.view.ScrollingView", options);
  }

  private void startTracking(final @Nullable Window window, final @NotNull Context context) {
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
              new WeakReference<>(window), hub, options, isAndroidXScrollViewAvailable);
      window.setCallback(new SentryWindowCallback(delegate, context, gestureListener, options));
    }
  }

  private void stopTracking(final @Nullable Window window) {
    if (window == null) {
      if (options != null) {
        options.getLogger().log(SentryLevel.INFO, "Window was null in stopTracking");
      }
      return;
    }

    final Window.Callback current = window.getCallback();
    if (current instanceof SentryWindowCallback) {
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
  public void onActivityStarted(@NotNull Activity activity) {}

  @Override
  public void onActivityResumed(@NotNull Activity activity) {
    startTracking(activity.getWindow(), activity);
  }

  @Override
  public void onActivityPaused(@NotNull Activity activity) {
    stopTracking(activity.getWindow());
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
  }
}
