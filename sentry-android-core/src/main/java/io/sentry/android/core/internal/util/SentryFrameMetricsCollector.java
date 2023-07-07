package io.sentry.android.core.internal.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Choreographer;
import android.view.FrameMetrics;
import android.view.Window;
import androidx.annotation.RequiresApi;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.util.Objects;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryFrameMetricsCollector implements Application.ActivityLifecycleCallbacks {
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull Set<Window> trackedWindows = new CopyOnWriteArraySet<>();
  private final @NotNull SentryOptions options;
  private @Nullable Handler handler;
  private @Nullable WeakReference<Window> currentWindow;
  private final @NotNull Map<String, FrameMetricsCollectorListener> listenerMap =
      new ConcurrentHashMap<>();
  private boolean isAvailable = false;
  private final WindowFrameMetricsManager windowFrameMetricsManager;

  private @Nullable Window.OnFrameMetricsAvailableListener frameMetricsAvailableListener;
  private @Nullable Choreographer choreographer;
  private @Nullable Field choreographerLastFrameTimeField;
  private long lastFrameStartNanos = 0;
  private long lastFrameEndNanos = 0;

  @SuppressLint("NewApi")
  public SentryFrameMetricsCollector(
      final @NotNull Context context,
      final @NotNull SentryOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    this(context, options, buildInfoProvider, new WindowFrameMetricsManager() {});
  }

  @SuppressWarnings("deprecation")
  @SuppressLint({"NewApi", "DiscouragedPrivateApi"})
  public SentryFrameMetricsCollector(
      final @NotNull Context context,
      final @NotNull SentryOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull WindowFrameMetricsManager windowFrameMetricsManager) {
    Objects.requireNonNull(context, "The context is required");
    this.options = Objects.requireNonNull(options, "SentryOptions is required");
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
        new HandlerThread("io.sentry.android.core.internal.util.SentryFrameMetricsCollector");
    handlerThread.setUncaughtExceptionHandler(
        (thread, e) ->
            options.getLogger().log(SentryLevel.ERROR, "Error during frames measurements.", e));
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());

    // We have to register the lifecycle callback, even if no profile is started, otherwise when we
    // start a profile, we wouldn't have the current activity and couldn't get the frameMetrics.
    ((Application) context).registerActivityLifecycleCallbacks(this);

    // Most considerations regarding timestamps of frames are inspired from JankStats library:
    // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:metrics/metrics-performance/src/main/java/androidx/metrics/performance/JankStatsApi24Impl.kt

    // The Choreographer instance must be accessed on the main thread
    new Handler(Looper.getMainLooper()).post(() -> choreographer = Choreographer.getInstance());
    // Let's get the last frame timestamp from the choreographer private field
    try {
      choreographerLastFrameTimeField = Choreographer.class.getDeclaredField("mLastFrameTimeNanos");
      choreographerLastFrameTimeField.setAccessible(true);
    } catch (NoSuchFieldException e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Unable to get the frame timestamp from the choreographer: ", e);
    }
    frameMetricsAvailableListener =
        (window, frameMetrics, dropCountSinceLastInvocation) -> {
          final long now = System.nanoTime();
          final float refreshRate =
              buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.R
                  ? window.getContext().getDisplay().getRefreshRate()
                  : window.getWindowManager().getDefaultDisplay().getRefreshRate();

          final long cpuDuration = getFrameCpuDuration(frameMetrics);
          long startTime = getFrameStartTimestamp(frameMetrics);
          // If we couldn't get the timestamp through reflection, we use current time
          if (startTime < 0) {
            startTime = now - cpuDuration;
          }
          // Let's "adjust" the start time of a frame to be after the end of the previous frame
          startTime = Math.max(startTime, lastFrameEndNanos);
          // Let's avoid emitting duplicates (start time equals to last start time)
          if (startTime == lastFrameStartNanos) {
            return;
          }
          lastFrameStartNanos = startTime;
          lastFrameEndNanos = startTime + cpuDuration;

          for (FrameMetricsCollectorListener l : listenerMap.values()) {
            l.onFrameMetricCollected(lastFrameEndNanos, cpuDuration, refreshRate);
          }
        };
  }

  /**
   * Return the internal timestamp in the choreographer of the last frame start timestamp through
   * reflection. On Android O the value is read from the frameMetrics itself.
   */
  @SuppressLint("NewApi")
  private long getFrameStartTimestamp(final @NotNull FrameMetrics frameMetrics) {

    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.O) {
      return frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP);
    }

    // Let's read the choreographer private field to get start timestamp of the frame, which
    // uses System.nanoTime() under the hood
    if (choreographer != null && choreographerLastFrameTimeField != null) {
      try {
        Long choreographerFrameStartTime =
            (Long) choreographerLastFrameTimeField.get(choreographer);
        if (choreographerFrameStartTime != null) {
          return choreographerFrameStartTime;
        }
      } catch (IllegalAccessException ignored) {
      }
    }
    return -1;
  }

  /**
   * Return time spent on the main thread (cpu) for frame creation. It doesn't consider time spent
   * on the render thread (gpu).
   */
  @RequiresApi(api = Build.VERSION_CODES.N)
  private long getFrameCpuDuration(final @NotNull FrameMetrics frameMetrics) {
    return frameMetrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
        + frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
        + frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION)
        + frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
        + frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
        + frameMetrics.getMetric(FrameMetrics.SYNC_DURATION);
  }

  // addOnFrameMetricsAvailableListener internally calls Activity.getWindow().getDecorView(),
  //  which cannot be called before setContentView. That's why we call it in onActivityStarted()
  @Override
  public void onActivityCreated(@NotNull Activity activity, @Nullable Bundle savedInstanceState) {}

  @Override
  public void onActivityStarted(@NotNull Activity activity) {
    setCurrentWindow(activity.getWindow());
  }

  @Override
  public void onActivityResumed(@NotNull Activity activity) {}

  @Override
  public void onActivityPaused(@NotNull Activity activity) {}

  @Override
  public void onActivityStopped(@NotNull Activity activity) {
    stopTrackingWindow(activity.getWindow());
    if (currentWindow != null && currentWindow.get() == activity.getWindow()) {
      currentWindow = null;
    }
  }

  @Override
  public void onActivitySaveInstanceState(@NotNull Activity activity, @NotNull Bundle outState) {}

  @Override
  public void onActivityDestroyed(@NotNull Activity activity) {}

  public @Nullable String startCollection(final @NotNull FrameMetricsCollectorListener listener) {
    if (!isAvailable) {
      return null;
    }
    final String uid = UUID.randomUUID().toString();
    listenerMap.put(uid, listener);
    trackCurrentWindow();
    return uid;
  }

  public void stopCollection(final @Nullable String listenerId) {
    if (!isAvailable) {
      return;
    }
    if (listenerId != null) {
      listenerMap.remove(listenerId);
    }
    Window window = currentWindow != null ? currentWindow.get() : null;
    if (window != null && listenerMap.isEmpty()) {
      stopTrackingWindow(window);
    }
  }

  @SuppressLint("NewApi")
  private void stopTrackingWindow(final @NotNull Window window) {
    if (trackedWindows.contains(window)) {
      if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.N) {
        try {
          windowFrameMetricsManager.removeOnFrameMetricsAvailableListener(
              window, frameMetricsAvailableListener);
        } catch (Exception e) {
          options
              .getLogger()
              .log(SentryLevel.ERROR, "Failed to remove frameMetricsAvailableListener", e);
        }
      }
      trackedWindows.remove(window);
    }
  }

  private void setCurrentWindow(final @NotNull Window window) {
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
    /**
     * Called when a frame is collected.
     *
     * @param frameEndNanos End timestamp of a frame in nanoseconds relative to System.nanotime().
     * @param durationNanos Duration in nanoseconds of the time spent from the cpu on the main
     *     thread to create the frame.
     * @param refreshRate Refresh rate of the screen.
     */
    void onFrameMetricCollected(
        final long frameEndNanos, final long durationNanos, final float refreshRate);
  }

  @ApiStatus.Internal
  public interface WindowFrameMetricsManager {
    @RequiresApi(api = Build.VERSION_CODES.N)
    default void addOnFrameMetricsAvailableListener(
        final @NotNull Window window,
        final @Nullable Window.OnFrameMetricsAvailableListener frameMetricsAvailableListener,
        final @Nullable Handler handler) {
      window.addOnFrameMetricsAvailableListener(frameMetricsAvailableListener, handler);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    default void removeOnFrameMetricsAvailableListener(
        final @NotNull Window window,
        final @Nullable Window.OnFrameMetricsAvailableListener frameMetricsAvailableListener) {
      window.removeOnFrameMetricsAvailableListener(frameMetricsAvailableListener);
    }
  }
}
