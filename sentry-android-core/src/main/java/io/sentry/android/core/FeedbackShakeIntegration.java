package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
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
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Detects shake gestures and shows the user feedback dialog when a shake is detected. {@link
 * io.sentry.SentryFeedbackOptions#isUseShakeGesture()} determines the initial state; it can be
 * toggled at runtime via {@code Sentry.feedback().enableOnShake()} and {@code
 * Sentry.feedback().disableOnShake()}.
 *
 * <p>Shake detection is scoped to the resumed activity: a dialog belongs to the window of the
 * activity that created it, so it can only ever be visible while that activity is resumed. Dialogs
 * report themselves via {@link #onDialogVisible(Activity, Dialog)} / {@link #onDialogGone(Dialog)}
 * and detection is then suppressed for the activity hosting them, which keeps a shake from stacking
 * a second dialog on top of a visible one without letting a dialog on a backgrounded activity
 * suppress detection elsewhere.
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
  private volatile @Nullable WeakReference<Activity> currentActivityRef;

  /**
   * The feedback dialogs that are currently visible, together with the activity hosting them. More
   * than one can be visible at a time, e.g. when the app calls {@code Sentry.feedback().show()}
   * while another dialog is already showing.
   */
  private final @NotNull CopyOnWriteArrayList<VisibleDialog> visibleDialogs =
      new CopyOnWriteArrayList<>();

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
      enableOnShake();
    }
  }

  @Override
  public synchronized void enableOnShake() {
    final @Nullable SentryAndroidOptions options = this.options;
    if (enabled || options == null) {
      return;
    }
    enabled = true;

    // Re-arm the detector in case it was closed before, either by disableOnShake() or by a previous
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
    application.registerActivityLifecycleCallbacks(this);
    options.getLogger().log(SentryLevel.DEBUG, "FeedbackShakeIntegration installed.");

    // In case of a deferred init or runtime enable, hook into any already-resumed activity
    final @Nullable Activity activity = CurrentActivityHolder.getInstance().getActivity();
    if (activity != null) {
      currentActivityRef = new WeakReference<>(activity);
      startShakeDetection(activity);
    }
  }

  @Override
  public synchronized void disableOnShake() {
    if (!enabled) {
      return;
    }
    enabled = false;

    application.unregisterActivityLifecycleCallbacks(this);
    shakeDetector.close();
    currentActivityRef = null;
  }

  @Override
  public boolean isOnShakeEnabled() {
    return enabled;
  }

  /**
   * Reports a feedback dialog as visible on {@code host}. Shake detection is suppressed for that
   * activity until the dialog reports back via {@link #onDialogGone(Dialog)}, so a shake can never
   * stack a second dialog on top of a visible one — no matter how the visible one was opened.
   */
  void onDialogVisible(final @NotNull Activity host, final @NotNull Dialog dialog) {
    visibleDialogs.add(new VisibleDialog(host, dialog));
    stopShakeDetection();
  }

  /** Reports a feedback dialog as no longer visible. Safe to call more than once per dialog. */
  void onDialogGone(final @NotNull Dialog dialog) {
    if (!removeDialog(dialog)) {
      return;
    }
    final @Nullable WeakReference<Activity> currentRef = currentActivityRef;
    final @Nullable Activity current = currentRef == null ? null : currentRef.get();
    if (enabled && current != null) {
      startShakeDetection(current);
    }
  }

  private boolean removeDialog(final @NotNull Dialog dialog) {
    boolean removed = false;
    for (final @NotNull VisibleDialog visibleDialog : visibleDialogs) {
      // Drop entries whose dialog was collected without reporting back, so they can't suppress
      // detection forever.
      final @Nullable Dialog trackedDialog = visibleDialog.dialogRef.get();
      if (trackedDialog == dialog) {
        removed = visibleDialogs.remove(visibleDialog) || removed;
      } else if (trackedDialog == null) {
        visibleDialogs.remove(visibleDialog);
      }
    }
    return removed;
  }

  private boolean hasDialogOn(final @NotNull Activity activity) {
    for (final @NotNull VisibleDialog visibleDialog : visibleDialogs) {
      if (visibleDialog.dialogRef.get() != null && visibleDialog.activityRef.get() == activity) {
        return true;
      }
    }
    return false;
  }

  @TestOnly
  @Nullable
  Activity getDialogActivity() {
    for (final @NotNull VisibleDialog visibleDialog : visibleDialogs) {
      if (visibleDialog.dialogRef.get() != null) {
        return visibleDialog.activityRef.get();
      }
    }
    return null;
  }

  /** Creates the dialog shown on shake. Replaceable in tests to simulate a failing show(). */
  interface DialogFactory {
    @NotNull
    Dialog create(final @NotNull Activity activity);
  }

  private @NotNull DialogFactory dialogFactory =
      activity -> new SentryUserFeedbackForm.Builder(activity).create();

  @TestOnly
  void setDialogFactory(final @NotNull DialogFactory dialogFactory) {
    this.dialogFactory = dialogFactory;
  }

  private static final class VisibleDialog {
    private final @NotNull WeakReference<Activity> activityRef;
    private final @NotNull WeakReference<Dialog> dialogRef;

    VisibleDialog(final @NotNull Activity activity, final @NotNull Dialog dialog) {
      this.activityRef = new WeakReference<>(activity);
      this.dialogRef = new WeakReference<>(dialog);
    }
  }

  @Override
  public void close() throws IOException {
    disableOnShake();
    visibleDialogs.clear();
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
    if (options == null) {
      return;
    }
    // Stop any existing detection (e.g. when transitioning between activities)
    stopShakeDetection();
    // A dialog is already visible here, so a shake could only stack a second one on top of it.
    // The dialog has no detector of its own in this case: SentryUserFeedbackForm only starts one
    // while shake-to-report is globally disabled, which is exactly when this integration is not
    // detecting either.
    if (hasDialogOn(activity)) {
      return;
    }
    shakeDetector.start(
        activity,
        () -> {
          final @Nullable WeakReference<Activity> ref = currentActivityRef;
          final Activity active = ref != null ? ref.get() : null;
          final Boolean inBackground = AppState.getInstance().isInBackground();
          if (active == null
              || options == null
              || !enabled
              || hasDialogOn(active)
              || Boolean.TRUE.equals(inBackground)) {
            return;
          }
          active.runOnUiThread(
              () -> {
                // Re-check on the main thread: shake-to-report may have been disabled, or an
                // earlier queued shake may have shown a dialog in the meantime (the dialog reports
                // itself synchronously in onStart).
                if (!enabled
                    || hasDialogOn(active)
                    || active.isFinishing()
                    || active.isDestroyed()) {
                  return;
                }
                @Nullable Dialog dialog = null;
                try {
                  dialog = dialogFactory.create(active);
                  dialog.show();
                } catch (Throwable e) {
                  if (dialog != null) {
                    onDialogGone(dialog);
                  }
                  options
                      .getLogger()
                      .log(SentryLevel.ERROR, "Failed to show feedback dialog on shake.", e);
                }
              });
        });
  }

  private void stopShakeDetection() {
    shakeDetector.stop();
  }
}
