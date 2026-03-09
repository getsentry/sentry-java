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
public final class FeedbackShakeIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;
  private final @NotNull SentryShakeDetector shakeDetector;
  private @Nullable SentryAndroidOptions options;
  private volatile @Nullable Activity currentActivity;
  private volatile boolean isDialogShowing = false;
  private volatile @Nullable Runnable previousOnFormClose;

  public FeedbackShakeIntegration(final @NotNull Application application) {
    this.application = Objects.requireNonNull(application, "Application is required");
    this.shakeDetector = new SentryShakeDetector(io.sentry.NoOpLogger.getInstance());
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions sentryOptions) {
    this.options = (SentryAndroidOptions) sentryOptions;

    if (!this.options.getFeedbackOptions().isUseShakeGesture()) {
      return;
    }

    // Re-assign a properly configured detector logger now that options are available
    shakeDetector.init(application);

    addIntegrationToSdkVersion("FeedbackShake");
    application.registerActivityLifecycleCallbacks(this);
    options.getLogger().log(SentryLevel.DEBUG, "FeedbackShakeIntegration installed.");

    // In case of a deferred init, hook into any already-resumed activity
    final @Nullable Activity activity = CurrentActivityHolder.getInstance().getActivity();
    if (activity != null) {
      currentActivity = activity;
      startShakeDetection(activity);
    }
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
    // Only stop if this is the activity we're tracking. When transitioning between
    // activities, B.onResume may fire before A.onPause — stopping unconditionally
    // would kill shake detection for the new activity.
    if (activity == currentActivity) {
      stopShakeDetection();
      currentActivity = null;
    }
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
  public void onActivityDestroyed(final @NotNull Activity activity) {
    // Only reset if this is the activity that hosts the dialog — the dialog cannot
    // outlive its host activity being destroyed.
    if (isDialogShowing && activity == currentActivity) {
      isDialogShowing = false;
      if (options != null) {
        options.getFeedbackOptions().setOnFormClose(previousOnFormClose);
      }
      previousOnFormClose = null;
    }
  }

  private void startShakeDetection(final @NotNull Activity activity) {
    if (options == null) {
      return;
    }
    // Stop any existing detection (e.g. when transitioning between activities)
    stopShakeDetection();
    shakeDetector.start(
        activity,
        () -> {
          final Activity active = currentActivity;
          final Boolean inBackground = AppState.getInstance().isInBackground();
          if (active != null
              && options != null
              && !isDialogShowing
              && !Boolean.TRUE.equals(inBackground)) {
            active.runOnUiThread(
                () -> {
                  if (isDialogShowing) {
                    return;
                  }
                  try {
                    isDialogShowing = true;
                    previousOnFormClose = options.getFeedbackOptions().getOnFormClose();
                    options
                        .getFeedbackOptions()
                        .setOnFormClose(
                            () -> {
                              isDialogShowing = false;
                              options.getFeedbackOptions().setOnFormClose(previousOnFormClose);
                              if (previousOnFormClose != null) {
                                previousOnFormClose.run();
                              }
                              previousOnFormClose = null;
                            });
                    new SentryUserFeedbackDialog.Builder(active).create().show();
                  } catch (Throwable e) {
                    isDialogShowing = false;
                    options.getFeedbackOptions().setOnFormClose(previousOnFormClose);
                    previousOnFormClose = null;
                    options
                        .getLogger()
                        .log(SentryLevel.ERROR, "Failed to show feedback dialog on shake.", e);
                  }
                });
          }
        });
  }

  private void stopShakeDetection() {
    shakeDetector.stop();
  }
}
