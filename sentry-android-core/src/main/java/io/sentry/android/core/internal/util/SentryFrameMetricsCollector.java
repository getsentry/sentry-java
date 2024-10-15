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
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.android.core.ContextUtils;
import io.sentry.util.Objects;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryFrameMetricsCollector implements Application.ActivityLifecycleCallbacks {
  private static final long oneSecondInNanos = TimeUnit.SECONDS.toNanos(1);
  private static final long frozenFrameThresholdNanos = TimeUnit.MILLISECONDS.toNanos(700);

  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull Set<Window> trackedWindows = new CopyOnWriteArraySet<>();
  private final @NotNull ILogger logger;
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

  @SuppressLint("NewApi")
  public SentryFrameMetricsCollector(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    this(context, logger, buildInfoProvider, new WindowFrameMetricsManager() {});
  }

  @SuppressWarnings("deprecation")
  @SuppressLint({"NewApi", "DiscouragedPrivateApi"})
  public SentryFrameMetricsCollector(
      final @NotNull Context context,
      final @NotNull SentryOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull WindowFrameMetricsManager windowFrameMetricsManager) {
    this(context, options.getLogger(), buildInfoProvider, windowFrameMetricsManager);
  }

  @SuppressWarnings("deprecation")
  @SuppressLint({"NewApi", "DiscouragedPrivateApi"})
  public SentryFrameMetricsCollector(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull WindowFrameMetricsManager windowFrameMetricsManager) {
    final @NotNull Context appContext =
        Objects.requireNonNull(
            ContextUtils.getApplicationContext(context), "The context is required");
    this.logger = Objects.requireNonNull(logger, "Logger is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
    this.windowFrameMetricsManager =
        Objects.requireNonNull(windowFrameMetricsManager, "WindowFrameMetricsManager is required");

    // registerActivityLifecycleCallbacks is only available if Context is an AppContext
    if (!(appContext instanceof Application)) {
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
        (thread, e) -> logger.log(SentryLevel.ERROR, "Error during frames measurements.", e));
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());

    // We have to register the lifecycle callback, even if no profile is started, otherwise when we
    // start a profile, we wouldn't have the current activity and couldn't get the frameMetrics.
    ((Application) appContext).registerActivityLifecycleCallbacks(this);

    // Most considerations regarding timestamps of frames are inspired from JankStats library:
    // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:metrics/metrics-performance/src/main/java/androidx/metrics/performance/JankStatsApi24Impl.kt

    // The Choreographer instance must be accessed on the main thread
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              try {
                choreographer = Choreographer.getInstance();
              } catch (Throwable e) {
                logger.log(
                    SentryLevel.ERROR,
                    "Error retrieving Choreographer instance. Slow and frozen frames will not be reported.",
                    e);
              }
            });
    // Let's get the last frame timestamp from the choreographer private field
    try {
      choreographerLastFrameTimeField = Choreographer.class.getDeclaredField("mLastFrameTimeNanos");
      choreographerLastFrameTimeField.setAccessible(true);
    } catch (NoSuchFieldException e) {
      logger.log(
          SentryLevel.ERROR, "Unable to get the frame timestamp from the choreographer: ", e);
    }

    frameMetricsAvailableListener =
        (window, frameMetrics, dropCountSinceLastInvocation) -> {
          final long now = System.nanoTime();
          final float refreshRate =
              buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.R
                  ? window.getContext().getDisplay().getRefreshRate()
                  : window.getWindowManager().getDefaultDisplay().getRefreshRate();

          final long expectedFrameDuration = (long) (oneSecondInNanos / refreshRate);

          final long cpuDuration = getFrameCpuDuration(frameMetrics);

          // if totalDurationNanos is smaller than expectedFrameTimeNanos,
          // it means that the frame was drawn within it's time budget, thus 0 delay
          final long delayNanos = Math.max(0, cpuDuration - expectedFrameDuration);

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

          // Most frames take just a few nanoseconds longer than the optimal calculated
          // duration.
          // Therefore we subtract one, because otherwise almost all frames would be slow.
          final boolean isSlow =
              isSlow(cpuDuration, (long) ((float) oneSecondInNanos / (refreshRate - 1.0f)));
          final boolean isFrozen = isSlow && isFrozen(cpuDuration);

          for (FrameMetricsCollectorListener l : listenerMap.values()) {
            l.onFrameMetricCollected(
                startTime,
                lastFrameEndNanos,
                cpuDuration,
                delayNanos,
                isSlow,
                isFrozen,
                refreshRate);
          }
        };
  }

  public static boolean isFrozen(long frameDuration) {
    return frameDuration > frozenFrameThresholdNanos;
  }

  public static boolean isSlow(long frameDuration, final long expectedFrameDuration) {
    return frameDuration > expectedFrameDuration;
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

    return getLastKnownFrameStartTimeNanos();
  }

  /**
   * Return time spent on the main thread (cpu) for frame creation. It doesn't consider time spent
   * on the render thread (gpu).
   */
  @RequiresApi(api = Build.VERSION_CODES.N)
  private long getFrameCpuDuration(final @NotNull FrameMetrics frameMetrics) {
    // Inspired by JankStats
    // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:metrics/metrics-performance/src/main/java/androidx/metrics/performance/JankStatsApi24Impl.kt;l=74-79;drc=1de6215c6bd9e887e3d94556e9ac55cfb7b8c797
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
          logger.log(SentryLevel.ERROR, "Failed to remove frameMetricsAvailableListener", e);
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

  /**
   * @return the last known time a frame was started, according to the Choreographer
   */
  public long getLastKnownFrameStartTimeNanos() {
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

  @ApiStatus.Internal
  public interface FrameMetricsCollectorListener {
    /**
     * Called when a frame is collected.
     *
     * @param frameStartNanos Start timestamp of a frame in nanoseconds relative to
     *     System.nanotime().
     * @param frameEndNanos End timestamp of a frame in nanoseconds relative to System.nanotime().
     * @param durationNanos Duration in nanoseconds of the time spent from the cpu on the main
     *     thread to create the frame.
     * @param delayNanos the frame delay, in nanoseconds.
     * @param isSlow True if the frame is considered slow, rendering taking longer than the
     *     refresh-rate based budget, false otherwise.
     * @param isFrozen True if the frame is considered frozen, rendering taking longer than 700ms,
     *     false otherwise.
     * @param refreshRate the last known refresh rate when the frame was rendered.
     */
    void onFrameMetricCollected(
        final long frameStartNanos,
        final long frameEndNanos,
        final long durationNanos,
        final long delayNanos,
        final boolean isSlow,
        final boolean isFrozen,
        final float refreshRate);
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
