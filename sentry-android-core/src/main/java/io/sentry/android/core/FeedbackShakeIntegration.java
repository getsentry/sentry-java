package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryFeedbackOptions;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects shake gestures and shows the user feedback dialog when a shake is detected. {@link
 * io.sentry.SentryFeedbackOptions#isUseShakeGesture()} determines the initial state; it can be
 * toggled at runtime via {@code Sentry.feedback().enableFeedbackOnShake()} and {@code
 * Sentry.feedback().disableFeedbackOnShake()}.
 *
 * <p>A single detector serves both the global toggle and individual dialogs set via {@link
 * #setDialog(SentryFeedbackOptions.IShakeDialog, boolean)}. While a dialog is tracked, a shake on
 * its host activity re-shows that dialog instead of creating a new form — a no-op when it is
 * already visible, so a shake can never stack a second form on top of one that is showing.
 */
public final class FeedbackShakeIntegration
    implements Integration,
        Closeable,
        Application.ActivityLifecycleCallbacks,
        SentryFeedbackOptions.IShakeController {

  private final @NotNull Application application;
  private final @NotNull SentryShakeDetector shakeDetector;
  private @Nullable SentryAndroidOptions options;
  private volatile boolean enabled = false;
  private boolean detecting = false;
  // Strong reference on purpose: for a per-form opt-in the caller may not retain the created
  // form, so the controller must keep it alive to be able to show it on shake. Lifecycle
  // callbacks stay registered as long as a dialog is tracked, so the reference is guaranteed
  // to be cleared once the host activity goes away (or another activity is created on top).
  private volatile @Nullable SentryFeedbackOptions.IShakeDialog trackedDialog;
  private boolean dialogRequestedShakeDetection = false;
  private boolean callbacksRegistered = false;
  private volatile @Nullable WeakReference<Activity> currentActivityRef;

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

    final @NotNull SentryAndroidOptions options = this.options;

    // Always expose the runtime toggle, even when the option starts out disabled.
    options.getFeedbackOptions().setShakeController(this);

    if (options.getFeedbackOptions().isUseShakeGesture()) {
      enable();
    }
  }

  @Override
  public synchronized void enable() {
    final @Nullable SentryAndroidOptions options = this.options;
    if (enabled || options == null) {
      return;
    }
    enabled = true;
    startDetecting(options);
  }

  @Override
  public synchronized void disable() {
    if (!enabled) {
      return;
    }
    enabled = false;
    if (!dialogRequestedShakeDetection || trackedDialog == null) {
      stopDetecting();
    }
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public synchronized void setDialog(
      final @Nullable SentryFeedbackOptions.IShakeDialog dialog,
      final boolean startShakeDetection) {
    final @Nullable SentryAndroidOptions options = this.options;
    if (options == null) {
      return;
    }
    if (dialog == null) {
      trackedDialog = null;
      dialogRequestedShakeDetection = false;
      if (!enabled) {
        stopDetecting();
      }
      updateCallbackRegistration();
      return;
    }
    trackedDialog = dialog;
    dialogRequestedShakeDetection = startShakeDetection;
    if (startShakeDetection) {
      startDetecting(options);
    } else if (!enabled) {
      // The previous dialog may have been the only reason detection was running.
      stopDetecting();
    }
    // Even without detection, keep listening for the tracked dialog's host activity being
    // destroyed, so the strong dialog reference can never outlive it (no activity leak).
    updateCallbackRegistration();
  }

  /**
   * Lifecycle callbacks are needed while shake detection runs (to follow the current activity) or
   * while a dialog is tracked (to release it when its host activity goes away).
   */
  private synchronized void updateCallbackRegistration() {
    final boolean needed = detecting || trackedDialog != null;
    if (needed && !callbacksRegistered) {
      callbacksRegistered = true;
      application.registerActivityLifecycleCallbacks(this);
    } else if (!needed && callbacksRegistered) {
      callbacksRegistered = false;
      application.unregisterActivityLifecycleCallbacks(this);
    }
  }

  private synchronized void startDetecting(final @NotNull SentryAndroidOptions options) {
    if (detecting) {
      return;
    }
    detecting = true;

    // Re-arm the detector in case it was closed before, either by stopDetecting() or by a previous
    // close() (e.g. a second Sentry.init reusing the same options), otherwise the closed latch
    // would keep shake detection off permanently.
    shakeDetector.reopen();

    // Resolving the accelerometer is the most expensive part of init (the first SensorManager
    // access), so warm it up off the main thread. start() re-runs init() on demand, so shake
    // detection still works if an activity resumes before this completes.
    try {
      options
          .getExecutorService()
          .submit(() -> shakeDetector.init(application, options.getLogger()));
    } catch (Throwable t) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Failed to submit shake detector initialization.", t);
    }

    addIntegrationToSdkVersion("FeedbackShake");
    updateCallbackRegistration();
    options.getLogger().log(SentryLevel.DEBUG, "FeedbackShakeIntegration installed.");

    // In case of a deferred init or runtime enable, hook into any already-resumed activity
    final @Nullable Activity activity = CurrentActivityHolder.getInstance().getActivity();
    if (activity != null) {
      currentActivityRef = new WeakReference<>(activity);
      startShakeDetection(activity);
    }
  }

  private synchronized void stopDetecting() {
    if (!detecting) {
      return;
    }
    detecting = false;

    updateCallbackRegistration();
    shakeDetector.close();
    currentActivityRef = null;
  }

  @Override
  public synchronized void close() throws IOException {
    enabled = false;
    trackedDialog = null;
    dialogRequestedShakeDetection = false;
    stopDetecting();
    // stopDetecting is a no-op when detection wasn't running, but a tracking-only dialog may
    // still have kept the callbacks registered.
    updateCallbackRegistration();
  }

  @Override
  public void onActivityResumed(final @NotNull Activity activity) {
    currentActivityRef = new WeakReference<>(activity);
    startShakeDetection(activity);
  }

  @Override
  public void onActivityPaused(final @NotNull Activity activity) {
    // Only stop if this is the activity we're tracking. When transitioning between
    // activities, B.onResume may fire before A.onPause — stopping unconditionally
    // would kill shake detection for the new activity.
    final @Nullable WeakReference<Activity> currentRef = currentActivityRef;
    final @Nullable Activity current = currentRef != null ? currentRef.get() : null;
    if (activity == current) {
      stopShakeDetection();
      currentActivityRef = null;
    }
  }

  @Override
  public void onActivityCreated(
      final @NotNull Activity activity, final @Nullable Bundle savedInstanceState) {
    // The user is navigating to a new activity: a dialog hosted by a different activity can't
    // be shown there, so stop tracking it (also releasing the strong reference early instead
    // of waiting for the host activity to be destroyed).
    final @Nullable SentryFeedbackOptions.IShakeDialog dialog = trackedDialog;
    if (dialog != null && findDialogActivity(dialog) != activity) {
      setDialog(null, false);
    }
  }

  @Override
  public void onActivityStarted(final @NotNull Activity activity) {}

  @Override
  public void onActivityStopped(final @NotNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(
      final @NotNull Activity activity, final @NotNull Bundle outState) {}

  @Override
  public void onActivityDestroyed(final @NotNull Activity activity) {
    // A tracked dialog cannot outlive its host activity; drop it so detection doesn't keep
    // running for it (and a shake can't try to show a dead dialog).
    final @Nullable SentryFeedbackOptions.IShakeDialog dialog = trackedDialog;
    if (dialog != null && findDialogActivity(dialog) == activity) {
      setDialog(null, false);
    }
  }

  private static @Nullable Activity findDialogActivity(
      final @NotNull SentryFeedbackOptions.IShakeDialog dialog) {
    if (dialog instanceof Dialog) {
      @Nullable Context context = ((Dialog) dialog).getContext();
      while (context instanceof ContextWrapper) {
        if (context instanceof Activity) {
          return (Activity) context;
        }
        context = ((ContextWrapper) context).getBaseContext();
      }
    }
    return null;
  }

  private void startShakeDetection(final @NotNull Activity activity) {
    if (options == null) {
      return;
    }
    // Stop any existing detection (e.g. when transitioning between activities)
    stopShakeDetection();
    // When detection runs only for a tracked dialog, don't listen on other activities —
    // a shake there couldn't show the dialog anyway.
    if (!enabled) {
      final @Nullable SentryFeedbackOptions.IShakeDialog dialog = trackedDialog;
      if (dialog == null || findDialogActivity(dialog) != activity) {
        return;
      }
    }
    shakeDetector.start(
        activity,
        () -> {
          final @Nullable WeakReference<Activity> ref = currentActivityRef;
          final Activity active = ref != null ? ref.get() : null;
          final Boolean inBackground = AppState.getInstance().isInBackground();
          if (active == null || options == null || Boolean.TRUE.equals(inBackground)) {
            return;
          }
          // Decide on the main thread: show() sets the tracked dialog synchronously, so a
          // second queued shake sees the form shown by the first instead of creating another.
          active.runOnUiThread(
              () -> {
                if (active.isFinishing() || active.isDestroyed()) {
                  return;
                }
                // A dialog tracked for the active activity takes precedence over creating a
                // new form — re-showing it is a no-op while it's already visible.
                final @Nullable SentryFeedbackOptions.IShakeDialog dialog = trackedDialog;
                if (dialog != null && findDialogActivity(dialog) == active) {
                  dialog.show();
                  return;
                }
                if (enabled) {
                  try {
                    new SentryUserFeedbackForm.Builder(active).create().show();
                  } catch (Throwable e) {
                    options
                        .getLogger()
                        .log(SentryLevel.ERROR, "Failed to show feedback dialog on shake.", e);
                  }
                }
              });
        });
  }

  private void stopShakeDetection() {
    shakeDetector.stop();
  }
}
