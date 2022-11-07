package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.FrameMetrics;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryFrameMetricsCollector implements Application.ActivityLifecycleCallbacks {
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull Set<Window> trackedWindows = new HashSet<>();
  private @Nullable Handler handler;
  private @Nullable WeakReference<Window> currentWindow;
  private final @NotNull HashMap<String, FrameMetricsCollectorListener> listenerMap =
      new HashMap<>();
  private boolean isAvailable = false;
  private final WindowFrameMetricsManager windowFrameMetricsManager;

  private @Nullable Window.OnFrameMetricsAvailableListener frameMetricsAvailableListener;

  @SuppressWarnings("deprecation")
  @SuppressLint("NewApi")
  public SentryFrameMetricsCollector(
      @NotNull Context context,
      @NotNull SentryOptions options,
      @NotNull BuildInfoProvider buildInfoProvider) {
    this(context, options, buildInfoProvider, new WindowFrameMetricsManager() {});
  }

  @SuppressWarnings("deprecation")
  @SuppressLint("NewApi")
  public SentryFrameMetricsCollector(
      @NotNull Context context,
      @NotNull SentryOptions options,
      @NotNull BuildInfoProvider buildInfoProvider,
      @NotNull WindowFrameMetricsManager windowFrameMetricsManager) {
    Objects.requireNonNull(context, "The context is required");
    Objects.requireNonNull(options, "SentryOptions is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
    this.windowFrameMetricsManager =
        Objects.requireNonNull(windowFrameMetricsManager, "WindowFrameMetricsManager is required");

    // registerActivityLifecycleCallbacks is only available if Context is an AppContext
    if (!(context instanceof Application)) {
      return;
    }
    // FrameMetrics api is only available since sdk version N
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.N) {
      return;
    }
    isAvailable = true;

    HandlerThread handlerThread =
        new HandlerThread("io.sentry.android.core.SentryFrameMetricsCollector");
    handlerThread.setUncaughtExceptionHandler(
        (thread, e) ->
            options.getLogger().log(SentryLevel.ERROR, "Error during frames measurements.", e));
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());

    // We have to register the lifecycle callback, even if no profile is started, otherwise when we
    // start a profile, we wouldn't have the current activity and couldn't get the frameMetrics.
    ((Application) context).registerActivityLifecycleCallbacks(this);

    frameMetricsAvailableListener =
        (window, frameMetrics, dropCountSinceLastInvocation) -> {
          float refreshRate =
              buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.R
                  ? window.getContext().getDisplay().getRefreshRate()
                  : window.getWindowManager().getDefaultDisplay().getRefreshRate();
          for (FrameMetricsCollectorListener l : listenerMap.values()) {
            l.onFrameMetricCollected(frameMetrics, refreshRate);
          }
        };
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
    setCurrentWindow(activity.getWindow());
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {}

  @Override
  public void onActivityPaused(@NonNull Activity activity) {}

  @Override
  public void onActivityStopped(@NonNull Activity activity) {
    cleanCurrentWindow(activity.getWindow());
  }

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {}

  public @Nullable String startCollection(FrameMetricsCollectorListener listener) {
    if (!isAvailable) {
      return null;
    }
    final String uid = UUID.randomUUID().toString();
    listenerMap.put(uid, listener);
    trackCurrentWindow();
    return uid;
  }

  public void stopCollection(@Nullable String listenerId) {
    if (!isAvailable) {
      return;
    }
    if (listenerId != null) {
      listenerMap.remove(listenerId);
    }
    Window window = currentWindow != null ? currentWindow.get() : null;
    if (window != null && listenerMap.isEmpty()) {
      cleanCurrentWindow(window);
    }
  }

  @SuppressLint("NewApi")
  private void cleanCurrentWindow(@NonNull Window window) {
    if (currentWindow != null && currentWindow.get() == window) {
      currentWindow = null;
    }
    if (trackedWindows.contains(window)) {
      if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.N) {
        windowFrameMetricsManager.removeOnFrameMetricsAvailableListener(
            window, frameMetricsAvailableListener);
      }
      trackedWindows.remove(window);
    }
  }

  private void setCurrentWindow(@NotNull Window window) {
    if (currentWindow != null && currentWindow.get() == window) {
      return;
    }
    currentWindow = new WeakReference<>(window);
    trackCurrentWindow();
  }

  @SuppressLint("NewApi")
  private void trackCurrentWindow() {
    Window window = currentWindow != null ? currentWindow.get() : null;
    if (window == null || !isAvailable) {
      return;
    }

    if (!trackedWindows.contains(window) && !listenerMap.isEmpty()) {

      if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.N && handler != null) {
        trackedWindows.add(window);
        windowFrameMetricsManager.addOnFrameMetricsAvailableListener(
            window, frameMetricsAvailableListener, handler);
      }
    }
  }

  @ApiStatus.Internal
  public interface FrameMetricsCollectorListener {
    void onFrameMetricCollected(@NotNull FrameMetrics frameMetrics, float refreshRate);
  }

  @ApiStatus.Internal
  public interface WindowFrameMetricsManager {
    @RequiresApi(api = Build.VERSION_CODES.N)
    default void addOnFrameMetricsAvailableListener(
        @NotNull Window window,
        @Nullable Window.OnFrameMetricsAvailableListener frameMetricsAvailableListener,
        @Nullable Handler handler) {
      window.addOnFrameMetricsAvailableListener(frameMetricsAvailableListener, handler);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    default void removeOnFrameMetricsAvailableListener(
        @NotNull Window window,
        @Nullable Window.OnFrameMetricsAvailableListener frameMetricsAvailableListener) {
      window.removeOnFrameMetricsAvailableListener(frameMetricsAvailableListener);
    }
  }
}
