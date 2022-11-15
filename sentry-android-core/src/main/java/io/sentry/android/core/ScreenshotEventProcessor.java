package io.sentry.android.core;

import static io.sentry.TypeCheckHint.ANDROID_ACTIVITY;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.sentry.Attachment;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * ScreenshotEventProcessor responsible for taking a screenshot of the screen when an error is
 * captured.
 */
@ApiStatus.Internal
public final class ScreenshotEventProcessor
    implements EventProcessor, Application.ActivityLifecycleCallbacks, Closeable {

  private static @NotNull ScreenshotEventProcessor instance;

  private final @NotNull Application application;
  private final @NotNull SentryAndroidOptions options;
  private @Nullable WeakReference<Activity> currentActivity;
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

  public static ScreenshotEventProcessor createInstance(
      final @NotNull Application application,
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    ScreenshotEventProcessor.instance =
        new ScreenshotEventProcessor(application, options, buildInfoProvider);
    return ScreenshotEventProcessor.instance;
  }

  public static ScreenshotEventProcessor getInstance() {
    return ScreenshotEventProcessor.instance;
  }

  public void setCurrentActivity(@NonNull Activity activity) {
    if (currentActivity != null && currentActivity.get() == activity) {
      return;
    }
    currentActivity = new WeakReference<>(activity);
  }

  public byte[] takeScreenshot() {
    if (currentActivity == null) {
      return null;
    }

    final Activity activity = currentActivity.get();
    if (!isActivityValid(activity)
        || activity.getWindow() == null
        || activity.getWindow().getDecorView() == null
        || activity.getWindow().getDecorView().getRootView() == null) {
      this.options
          .getLogger()
          .log(SentryLevel.DEBUG, "Activity isn't valid, not taking screenshot.");
      return null;
    }

    final View view = activity.getWindow().getDecorView().getRootView();
    if (view.getWidth() <= 0 || view.getHeight() <= 0) {
      this.options
          .getLogger()
          .log(SentryLevel.DEBUG, "View's width and height is zeroed, not taking screenshot.");
      return null;
    }

    try {
      // ARGB_8888 -> This configuration is very flexible and offers the best quality
      final Bitmap bitmap =
          Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);

      final Canvas canvas = new Canvas(bitmap);
      view.draw(canvas);

      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

      // 0 meaning compress for small size, 100 meaning compress for max quality.
      // Some formats, like PNG which is lossless, will ignore the quality setting.
      bitmap.compress(Bitmap.CompressFormat.PNG, 0, byteArrayOutputStream);

      if (byteArrayOutputStream.size() <= 0) {
        this.options
            .getLogger()
            .log(SentryLevel.DEBUG, "Screenshot is 0 bytes, not attaching the image.");
        return null;
      }

      // screenshot png is around ~100-150 kb
      return byteArrayOutputStream.toByteArray();
    } catch (Throwable e) {
      this.options.getLogger().log(SentryLevel.ERROR, "Taking screenshot failed.", e);
    }
    return null;
  }

  @SuppressWarnings("NullAway")
  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, @NotNull Hint hint) {
    if (HintUtils.getIsFromHybridSdk(hint)
        || !lifecycleCallbackInstalled
        || !event.isErrored()
        || currentActivity == null) {
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

    final byte[] screenshot = takeScreenshot();
    if (screenshot == null) {
      return event;
    }

    hint.setScreenshot(Attachment.fromScreenshot(screenshot));
    hint.set(ANDROID_ACTIVITY, currentActivity.get());
    return event;
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    setCurrentActivity(activity);
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
      currentActivity = null;
    }
  }

  private void cleanCurrentActivity(@NonNull Activity activity) {
    if (currentActivity != null && currentActivity.get() == activity) {
      currentActivity = null;
    }
  }

  @SuppressLint("NewApi")
  private boolean isActivityValid(@Nullable Activity activity) {
    if (activity == null) {
      return false;
    }
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return !activity.isFinishing() && !activity.isDestroyed();
    } else {
      return !activity.isFinishing();
    }
  }
}
