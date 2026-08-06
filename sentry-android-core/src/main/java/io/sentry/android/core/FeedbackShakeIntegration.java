package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.Activity;
import android.app.Application;
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
 * <p>While any feedback dialog is visible it pauses shake handling via {@link
 * #pauseDetection(boolean)}, so a shake can never stack a second dialog on top of one that is
 * already showing — no matter how the visible dialog was opened.
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
  private volatile boolean paused = false;
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

    // Re-arm the detector in case it was closed before, either by disable() or by a previous
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
  public synchronized void disable() {
    if (!enabled) {
      return;
    }
    enabled = false;

    application.unregisterActivityLifecycleCallbacks(this);
    shakeDetector.close();
    currentActivityRef = null;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void pauseDetection(final boolean paused) {
    this.paused = paused;
  }

  @Override
  public void close() throws IOException {
    disable();
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
    shakeDetector.start(
        activity,
        () -> {
          final @Nullable WeakReference<Activity> ref = currentActivityRef;
          final Activity active = ref != null ? ref.get() : null;
          final Boolean inBackground = AppState.getInstance().isInBackground();
          if (active == null || options == null || paused || Boolean.TRUE.equals(inBackground)) {
            return;
          }
          active.runOnUiThread(
              () -> {
                // Re-check on the main thread: an earlier queued shake may have shown a form
                // in the meantime (the form pauses detection synchronously in onStart).
                if (paused || active.isFinishing() || active.isDestroyed()) {
                  return;
                }
                try {
                  new SentryUserFeedbackForm.Builder(active).create().show();
                } catch (Throwable e) {
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
