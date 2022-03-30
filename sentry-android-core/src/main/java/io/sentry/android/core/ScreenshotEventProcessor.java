package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.sentry.EventProcessor;
import io.sentry.SentryEvent;
import io.sentry.android.core.internal.util.MainThreadChecker;
import io.sentry.util.Objects;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
// import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class ScreenshotEventProcessor
    implements EventProcessor, Application.ActivityLifecycleCallbacks, Closeable {

  private final @NotNull Application application;
  private final @NotNull SentryAndroidOptions options;
  //  private final @NotNull WeakReference<Activity> currentActivity = new WeakReference();
  private @Nullable Activity activity;

  public ScreenshotEventProcessor(
      final @NotNull Application application, final @NotNull SentryAndroidOptions options) {
    this.application = Objects.requireNonNull(application, "Application is required");
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");

    if (this.options.isAttachScreenshot()) {
      application.registerActivityLifecycleCallbacks(this);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public @NotNull SentryEvent process(
      final @NotNull SentryEvent event, @Nullable Map<String, Object> hint) {
    if (options.isAttachScreenshot() && MainThreadChecker.isMainThread(Thread.currentThread())) {
      if (activity != null) {
        View view = activity.getWindow().getDecorView().getRootView();
        final boolean cacheEnabled = view.isDrawingCacheEnabled();
        view.setDrawingCacheEnabled(true);
        view.buildDrawingCache(true);

        Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, byteArrayOutputStream);

        if (hint == null) {
          hint = new HashMap<>();
        }
        hint.put("screenshot", byteArrayOutputStream.toByteArray());

        view.setDrawingCacheEnabled(cacheEnabled);
      }
    }

    return event;
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

  @Override
  public void onActivityStarted(@NonNull Activity activity) {}

  @Override
  public void onActivityResumed(@NonNull Activity activity) {
    this.activity = activity;
  }

  @Override
  public void onActivityPaused(@NonNull Activity activity) {
    this.activity = null;
  }

  @Override
  public void onActivityStopped(@NonNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {}

  @Override
  public void close() throws IOException {
    if (options.isAttachScreenshot()) {
      application.unregisterActivityLifecycleCallbacks(this);
    }
  }
}
