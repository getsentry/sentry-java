package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.Window;
import io.sentry.IScopes;
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
  private @Nullable IScopes scopes;
  private @Nullable SentryAndroidOptions options;

  private final boolean isAndroidXAvailable;

  public UserInteractionIntegration(
      final @NotNull Application application, final @NotNull io.sentry.util.LoadClass classLoader) {
    this.application = Objects.requireNonNull(application, "Application is required");
    isAndroidXAvailable =
        classLoader.isClassAvailable("androidx.core.view.GestureDetectorCompat", options);
  }

  private void startTracking(final @NotNull Activity activity) {
    final Window window = activity.getWindow();
    if (window == null) {
      if (options != null) {
        options.getLogger().log(SentryLevel.INFO, "Window was null in startTracking");
      }
      return;
    }

    if (scopes != null && options != null) {
      Window.Callback delegate = window.getCallback();
      if (delegate == null) {
        delegate = new NoOpWindowCallback();
      }

      if (delegate instanceof SentryWindowCallback) {
        // already instrumented
        return;
      }

      final SentryGestureListener gestureListener =
          new SentryGestureListener(activity, scopes, options);
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
  public void onActivityStarted(@NotNull Activity activity) {}

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
  public void register(@NotNull IScopes scopes, @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");

    final boolean integrationEnabled =
        this.options.isEnableUserInteractionBreadcrumbs()
            || this.options.isEnableUserInteractionTracing();
    this.options
        .getLogger()
        .log(SentryLevel.DEBUG, "UserInteractionIntegration enabled: %s", integrationEnabled);

    if (integrationEnabled) {
      if (isAndroidXAvailable) {
        application.registerActivityLifecycleCallbacks(this);
        this.options.getLogger().log(SentryLevel.DEBUG, "UserInteractionIntegration installed.");
        addIntegrationToSdkVersion("UserInteraction");
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
