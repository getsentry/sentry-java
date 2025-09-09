package io.sentry.android.replay.screenshot

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

internal class FrameTimingsTracker(app: Context) {

  companion object {
    private const val ANIMATION_THRESHOLD_NS = 1000000L // 1ms
    private const val LAYOUT_THRESHOLD_NS = 500000L // 0.5ms
  }

  @Volatile
  private var lastAnimDuration: Long = 0

  @Volatile
  private var lastTotalDuration: Long = 0

  @Volatile
  private var lastLayoutDuration: Long = 0

  private val handler = Handler(Looper.getMainLooper())

  init {
    (app as Application?)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          activity.window?.addOnFrameMetricsAvailableListener(listener, handler)
        }
      }

      override fun onActivityDestroyed(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          activity.window?.removeOnFrameMetricsAvailableListener(listener)
        }
      }

      override fun onActivityStarted(activity: Activity) {}
      override fun onActivityResumed(activity: Activity) {}
      override fun onActivityPaused(activity: Activity) {}
      override fun onActivityStopped(activity: Activity) {}
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    })
  }

  private val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      lastTotalDuration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
      lastAnimDuration = frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION)
      lastLayoutDuration = frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
    }
  }

  fun isIdle(): Boolean {
    Log.d("TAG", "isIdle: lastTotalDuration: ${lastTotalDuration/1000000.0}, lastAnimDuration: ${lastAnimDuration/1000000.0}, layoutDuration: ${lastLayoutDuration/1000000.0}")
    return lastAnimDuration < ANIMATION_THRESHOLD_NS && lastLayoutDuration < LAYOUT_THRESHOLD_NS
  }
}
