package io.sentry.android.core;

import static io.sentry.TypeCheckHint.ANDROID_ACTIVITY;
import static io.sentry.android.core.internal.util.ScreenshotUtils.takeScreenshot;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.sentry.Attachment;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * ScreenshotEventProcessor responsible for taking a screenshot of the screen when an error is
 * captured.
 */
@ApiStatus.Internal
public final class ScreenshotEventProcessor
    implements EventProcessor, Application.ActivityLifecycleCallbacks, Closeable {

  private final @NotNull Application application;
  private final @NotNull SentryAndroidOptions options;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private boolean lifecycleCallbackInstalled = true;

  public ScreenshotEventProcessor(
      final @NotNull Application application,
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    this.application = Objects.requireNonNull(application, "Application is required");
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");

    application.registerActivityLifecycleCallbacks(this);
  }

  @SuppressWarnings("NullAway")
  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, @NotNull Hint hint) {
    if (!lifecycleCallbackInstalled || !event.isErrored()) {
      return event;
    }
    if (!options.isAttachScreenshot()) {
      application.unregisterActivityLifecycleCallbacks(this);
      lifecycleCallbackInstalled = false;

      this.options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "attachScreenshot is disabled, ScreenshotEventProcessor isn't installed.");

      return event;
    }
    final Activity activity = CurrentActivityHolder.getInstance().getActivity();
    if (activity == null || HintUtils.isFromHybridSdk(hint)) {
      return event;
    }

    final byte[] screenshot = takeScreenshot(activity, options.getLogger(), buildInfoProvider);
    if (screenshot == null) {
      return event;
    }

    hint.setScreenshot(Attachment.fromScreenshot(screenshot));
    hint.set(ANDROID_ACTIVITY, activity);
    return event;
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    CurrentActivityHolder.getInstance().setActivity(activity);
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
    setCurrentActivity(activity);
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {
    setCurrentActivity(activity);
  }

  @Override
  public void onActivityPaused(@NonNull Activity activity) {
    cleanCurrentActivity(activity);
  }

  @Override
  public void onActivityStopped(@NonNull Activity activity) {
    cleanCurrentActivity(activity);
  }

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {
    cleanCurrentActivity(activity);
  }

  @Override
  public void close() throws IOException {
    if (options.isAttachScreenshot()) {
      application.unregisterActivityLifecycleCallbacks(this);
      CurrentActivityHolder.getInstance().clearActivity();
    }
  }

  private void cleanCurrentActivity(@NonNull Activity activity) {
    if (CurrentActivityHolder.getInstance().getActivity() == activity) {
      CurrentActivityHolder.getInstance().clearActivity();
    }
  }

  private void setCurrentActivity(@NonNull Activity activity) {
    CurrentActivityHolder.getInstance().setActivity(activity);
  }
}
