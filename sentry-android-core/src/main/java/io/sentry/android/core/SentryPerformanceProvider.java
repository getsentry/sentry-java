package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import androidx.annotation.NonNull;
import io.sentry.android.core.internal.gestures.NoOpWindowCallback;
import io.sentry.android.core.performance.ActivityLifecycleCallbacksAdapter;
import io.sentry.android.core.performance.ActivityLifecycleTimeSpan;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.NextDrawListener;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.android.core.performance.WindowContentChangedCallback;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class SentryPerformanceProvider extends EmptySecureContentProvider {

  private @Nullable Application app;
  private @Nullable Application.ActivityLifecycleCallbacks activityCallback;

  @Override
  public boolean onCreate() {
    onAppLaunched();
    return true;
  }

  @Override
  public void attachInfo(Context context, ProviderInfo info) {
    // applicationId is expected to be prepended. See AndroidManifest.xml
    if (SentryPerformanceProvider.class.getName().equals(info.authority)) {
      throw new IllegalStateException(
          "An applicationId is required to fulfill the manifest placeholder.");
    }
    super.attachInfo(context, info);
  }

  @Nullable
  @Override
  public String getType(@NotNull Uri uri) {
    return null;
  }

  @ApiStatus.Internal
  public void onAppLaunched() {
    // Process.getStartUptimeMillis() requires API level 24+
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
      return;
    }

    @Nullable Context context = getContext();
    if (context != null) {
      context = context.getApplicationContext();
    }
    if (context instanceof Application) {
      app = (Application) context;
    }
    if (app == null) {
      return;
    }

    final AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
    final TimeSpan appStartTimespan = appStartMetrics.getAppStartTimeSpan();
    appStartTimespan.setStartedAt(Process.getStartUptimeMillis());

    final AtomicBoolean firstDrawDone = new AtomicBoolean(false);
    final Handler handler = new Handler(Looper.getMainLooper());

    activityCallback =
        new ActivityLifecycleCallbacksAdapter() {
          final WeakHashMap<Activity, ActivityLifecycleTimeSpan> activityLifecycleMap =
              new WeakHashMap<>();

          @Override
          public void onActivityPreCreated(
              @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            final long now = SystemClock.uptimeMillis();
            if (appStartMetrics.getAppStartTimeSpan().hasStopped()) {
              return;
            }

            final ActivityLifecycleTimeSpan timeSpan = new ActivityLifecycleTimeSpan();
            timeSpan.onCreate.setStartedAt(now);
            activityLifecycleMap.put(activity, timeSpan);
          }

          @Override
          public void onActivityCreated(
              @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            if (appStartMetrics.getAppStartType() == AppStartMetrics.AppStartType.UNKNOWN) {
              appStartMetrics.setAppStartType(
                  savedInstanceState == null
                      ? AppStartMetrics.AppStartType.COLD
                      : AppStartMetrics.AppStartType.WARM);
            }
          }

          @Override
          public void onActivityPostCreated(
              @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            if (appStartMetrics.getAppStartTimeSpan().hasStopped()) {
              return;
            }

            final @Nullable ActivityLifecycleTimeSpan timeSpan = activityLifecycleMap.get(activity);
            if (timeSpan != null) {
              timeSpan.onCreate.stop();
              timeSpan.onCreate.setDescription(activity.getClass().getName() + ".onCreate");
            }
          }

          @Override
          public void onActivityPreStarted(@NonNull Activity activity) {
            final long now = SystemClock.uptimeMillis();
            if (appStartMetrics.getAppStartTimeSpan().hasStopped()) {
              return;
            }
            final @Nullable ActivityLifecycleTimeSpan timeSpan = activityLifecycleMap.get(activity);
            if (timeSpan != null) {
              timeSpan.onStart.setStartedAt(now);
            }
          }

          @Override
          public void onActivityStarted(@NonNull Activity activity) {
            if (firstDrawDone.get()) {
              return;
            }
            @Nullable Window window = activity.getWindow();
            if (window != null) {
              @Nullable View decorView = window.peekDecorView();
              if (decorView != null) {
                new NextDrawListener(
                        decorView,
                        () ->
                            handler.postAtFrontOfQueue(
                                () -> {
                                  if (firstDrawDone.compareAndSet(false, true)) {
                                    onAppStartDone();
                                  }
                                }))
                    .safelyRegisterForNextDraw();
              } else {
                @Nullable Window.Callback oldCallback = window.getCallback();
                if (oldCallback == null) {
                  oldCallback = new NoOpWindowCallback();
                }
                window.setCallback(
                    new WindowContentChangedCallback(
                        oldCallback,
                        () -> {
                          @Nullable View newDecorView = window.peekDecorView();
                          if (newDecorView != null) {
                            new NextDrawListener(
                                    newDecorView,
                                    () ->
                                        handler.postAtFrontOfQueue(
                                            () -> {
                                              if (firstDrawDone.compareAndSet(false, true)) {
                                                onAppStartDone();
                                              }
                                            }))
                                .safelyRegisterForNextDraw();
                          }
                        }));
              }
            }
          }

          @Override
          public void onActivityPostStarted(@NonNull Activity activity) {
            final @Nullable ActivityLifecycleTimeSpan timeSpan =
                activityLifecycleMap.remove(activity);
            if (appStartMetrics.getAppStartTimeSpan().hasStopped()) {
              return;
            }
            if (timeSpan != null) {
              timeSpan.onStart.stop();
              timeSpan.onStart.setDescription(activity.getClass().getName() + ".onStart");

              appStartMetrics.addActivityLifecycleTimeSpans(timeSpan);
            }
          }

          @Override
          public void onActivityDestroyed(@NonNull Activity activity) {
            // safety net for activities which were created but never stopped
            activityLifecycleMap.remove(activity);
          }
        };

    app.registerActivityLifecycleCallbacks(activityCallback);
  }

  private synchronized void onAppStartDone() {
    AppStartMetrics.getInstance().getAppStartTimeSpan().stop();

    if (app != null) {
      if (activityCallback != null) {
        app.unregisterActivityLifecycleCallbacks(activityCallback);
      }
    }
  }

  @TestOnly
  public @Nullable Application.ActivityLifecycleCallbacks getActivityCallback() {
    return activityCallback;
  }
}
