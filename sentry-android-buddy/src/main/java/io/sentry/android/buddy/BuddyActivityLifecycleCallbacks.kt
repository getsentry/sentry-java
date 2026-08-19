package io.sentry.android.buddy

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.sentry.android.buddy.ui.overlay.BuddyOverlayManager
import java.lang.ref.WeakReference

internal class BuddyActivityLifecycleCallbacks(
  private val recorder: BuddyRecorder,
  private var overlayManager: BuddyOverlayManager?,
) : Application.ActivityLifecycleCallbacks {
  private var currentActivity: WeakReference<Activity>? = null

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

  override fun onActivityStarted(activity: Activity) = Unit

  override fun onActivityResumed(activity: Activity) {
    currentActivity = WeakReference(activity)
    overlayManager?.attach(activity)
    recorder.makeCurrent()
    val screenName = activity.javaClass.simpleName
    recorder.recordScreen(screenName)
    overlayManager?.recordingEvent("Screen: $screenName")
  }

  override fun onActivityPaused(activity: Activity) {
    if (currentActivity?.get() === activity) {
      currentActivity = null
    }
    overlayManager?.detach(activity)
  }

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

  override fun onActivityDestroyed(activity: Activity) {
    if (currentActivity?.get() === activity) {
      currentActivity = null
    }
    overlayManager?.detach(activity)
  }

  fun recordCurrentScreen() {
    currentActivity?.get()?.let {
      val screenName = it.javaClass.simpleName
      recorder.recordScreen(screenName)
      overlayManager?.recordingEvent("Screen: $screenName")
    }
  }

  fun recordingEvent(text: String) {
    overlayManager?.recordingEvent(text)
  }

  fun updateOverlay(options: SentryBuddyOptions) {
    if (!options.showOverlay) {
      overlayManager?.detachAll()
      overlayManager = null
      return
    }
    if (overlayManager == null) {
      overlayManager =
        BuddyOverlayManager(
          SentryBuddySessionController(
            flowAnalysesApi = options.flowAnalysesApi,
            openUrlApi = options.openUrlApi,
          )
        )
    }
    overlayManager?.updateOptions(options)
  }

  fun detachAll() {
    overlayManager?.detachAll()
  }
}
