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
import java.lang.ref.WeakReference;
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
  private volatile @Nullable WeakReference<Activity> currentActivityRef;
  private volatile boolean isDialogShowing = false;
  private volatile @Nullable Runnable previousOnFormClose;

  public FeedbackShakeIntegration(final @NotNull Application application) {
    this.application = Objects.requireNonNull(application, "Application is required");
    this.shakeDetector = new SentryShakeDetector(io.sentry.NoOpLogger.getInstance());
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions sentryOptions) {
    this.options =
        Objects.requireNonNull(
            (sentryOptions instanceof SentryAndroidOptions)
                ? (SentryAndroidOptions) sentryOptions
                : null,
            "SentryAndroidOptions is required");

    if (!this.options.getFeedbackOptions().isUseShakeGesture()) {
      return;
    }

    shakeDetector.init(application, options.getLogger());

    addIntegrationToSdkVersion("FeedbackShake");
    application.registerActivityLifecycleCallbacks(this);
    options.getLogger().log(SentryLevel.DEBUG, "FeedbackShakeIntegration installed.");

    // In case of a deferred init, hook into any already-resumed activity
    final @Nullable Activity activity = CurrentActivityHolder.getInstance().getActivity();
    if (activity != null) {
      currentActivityRef = new WeakReference<>(activity);
      startShakeDetection(activity);
    }
  }

  @Override
  public void close() throws IOException {
    application.unregisterActivityLifecycleCallbacks(this);
    shakeDetector.close();
    // Restore onFormClose if a dialog is still showing, since lifecycle callbacks
    // are now unregistered and onActivityDestroyed cleanup won't fire.
    if (isDialogShowing) {
      isDialogShowing = false;
      if (options != null) {
        options.getFeedbackOptions().setOnFormClose(previousOnFormClose);
      }
      previousOnFormClose = null;
    }
    currentActivityRef = null;
  }

  @Override
  public void onActivityResumed(final @NotNull Activity activity) {
    // If a dialog is showing on a different activity (e.g. user navigated via notification),
    // clean up since the dialog's host activity is going away and onActivityDestroyed
    // won't match currentActivity anymore.
    final @Nullable Activity current = currentActivityRef != null ? currentActivityRef.get() : null;
    if (isDialogShowing && current != null && current != activity) {
      isDialogShowing = false;
      if (options != null) {
        options.getFeedbackOptions().setOnFormClose(previousOnFormClose);
      }
      previousOnFormClose = null;
    }
    currentActivityRef = new WeakReference<>(activity);
    startShakeDetection(activity);
  }

  @Override
  public void onActivityPaused(final @NotNull Activity activity) {
    // Only stop if this is the activity we're tracking. When transitioning between
    // activities, B.onResume may fire before A.onPause — stopping unconditionally
    // would kill shake detection for the new activity.
    final @Nullable Activity current = currentActivityRef != null ? currentActivityRef.get() : null;
    if (activity == current) {
      stopShakeDetection();
      // Keep currentActivityRef set when a dialog is showing so onActivityDestroyed
      // can still match and clean up. Otherwise the cleanup condition
      // (activity == current) would always be false since onPause fires
      // before onDestroy.
      if (!isDialogShowing) {
        currentActivityRef = null;
      }
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
    final @Nullable Activity current = currentActivityRef != null ? currentActivityRef.get() : null;
    if (isDialogShowing && activity == current) {
      isDialogShowing = false;
      currentActivityRef = null;
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
          final @Nullable WeakReference<Activity> ref = currentActivityRef;
          final Activity active = ref != null ? ref.get() : null;
          final Boolean inBackground = AppState.getInstance().isInBackground();
          if (active != null
              && options != null
              && !isDialogShowing
              && !Boolean.TRUE.equals(inBackground)) {
            active.runOnUiThread(
                () -> {
                  if (isDialogShowing || active.isFinishing() || active.isDestroyed()) {
                    return;
                  }
                  try {
                    isDialogShowing = true;
                    final Runnable captured = options.getFeedbackOptions().getOnFormClose();
                    previousOnFormClose = captured;
                    options
                        .getFeedbackOptions()
                        .setOnFormClose(
                            () -> {
                              isDialogShowing = false;
                              options.getFeedbackOptions().setOnFormClose(captured);
                              if (captured != null) {
                                captured.run();
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
