package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects shake gestures and shows the user feedback dialog when a shake is detected. Only active
 * when {@link io.sentry.SentryFeedbackOptions#isUseShakeGesture()} returns {@code true}.
 */
public final class ShakeDetectionIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;
  private @Nullable SentryShakeDetector shakeDetector;
  private @Nullable SentryAndroidOptions options;
  private @Nullable Activity currentActivity;

  public ShakeDetectionIntegration(final @NotNull Application application) {
    this.application = Objects.requireNonNull(application, "Application is required");
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions sentryOptions) {
    this.options = (SentryAndroidOptions) sentryOptions;

    if (!this.options.getFeedbackOptions().isUseShakeGesture()) {
      return;
    }

    addIntegrationToSdkVersion("ShakeDetection");
    application.registerActivityLifecycleCallbacks(this);
    options.getLogger().log(SentryLevel.DEBUG, "ShakeDetectionIntegration installed.");
  }

  @Override
  public void close() throws IOException {
    application.unregisterActivityLifecycleCallbacks(this);
    stopShakeDetection();
  }

  @Override
  public void onActivityResumed(final @NotNull Activity activity) {
    currentActivity = activity;
    startShakeDetection(activity);
  }

  @Override
  public void onActivityPaused(final @NotNull Activity activity) {
    stopShakeDetection();
    currentActivity = null;
  }

  @Override
  public void onActivityCreated(
      final @NotNull Activity activity, final @Nullable Bundle savedInstanceState) {}

  @Override
  public void onActivityStarted(final @NotNull Activity activity) {}

  @Override
  public void onActivityStopped(final @NotNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(
      final @NotNull Activity activity, final @NotNull Bundle outState) {}

  @Override
  public void onActivityDestroyed(final @NotNull Activity activity) {}

  private void startShakeDetection(final @NotNull Activity activity) {
    if (shakeDetector != null || options == null) {
      return;
    }
    shakeDetector = new SentryShakeDetector(options.getLogger());
    shakeDetector.start(
        activity,
        () -> {
          final Activity active = currentActivity;
          if (active != null && options != null) {
            active.runOnUiThread(
                () -> options.getFeedbackOptions().getDialogHandler().showDialog(null, null));
          }
        });
  }

  private void stopShakeDetection() {
    if (shakeDetector != null) {
      shakeDetector.stop();
      shakeDetector = null;
    }
  }
}
