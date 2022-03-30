package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.sentry.EventProcessor;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.util.Objects;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class ScreenshotEventProcessor
    implements EventProcessor, Application.ActivityLifecycleCallbacks, Closeable {

  private final @NotNull Application application;
  private final @NotNull SentryAndroidOptions options;
  private @Nullable WeakReference<Activity> currentActivity;

  public ScreenshotEventProcessor(
      final @NotNull Application application, final @NotNull SentryAndroidOptions options) {
    this.application = Objects.requireNonNull(application, "Application is required");
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");

    if (this.options.isAttachScreenshot()) {
      application.registerActivityLifecycleCallbacks(this);
    }
  }

  @Override
  public @NotNull SentryEvent process(
      final @NotNull SentryEvent event, @Nullable Map<String, Object> hint) {
    if (options.isAttachScreenshot() && event.isErrored() && currentActivity != null) {
      final Activity activity = currentActivity.get();
      if (activity != null
          && activity.getWindow() != null
          && activity.getWindow().getDecorView() != null
          && activity.getWindow().getDecorView().getRootView() != null) {
        final View view = activity.getWindow().getDecorView().getRootView();

        if (view.getWidth() > 0 && view.getHeight() > 0) {
          try {
            final Bitmap bitmap =
                Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);

            final Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, byteArrayOutputStream);

            if (hint == null) {
              hint = new HashMap<>();
            }

            if (byteArrayOutputStream.size() > 0) {
              hint.put("screenshot", byteArrayOutputStream.toByteArray());
            }
          } catch (Throwable e) {
            this.options.getLogger().log(SentryLevel.ERROR, "Taking screenshot failed.", e);
          }
        }
      }
    }

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

  private void setCurrentActivity(@NonNull Activity activity) {
    if (currentActivity != null && currentActivity.get() == activity) {
      return;
    }
    currentActivity = new WeakReference<>(activity);
  }
}
