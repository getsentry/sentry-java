package io.sentry.android.buddy.ui.overlay

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import io.sentry.android.buddy.BuddyScreenScanner
import io.sentry.android.buddy.SentryBuddyOptions
import io.sentry.android.buddy.SentryBuddySessionController
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.sentryUiLinks
import java.util.WeakHashMap

internal class BuddyOverlayManager(private val controller: SentryBuddySessionController) {
  private val overlays = WeakHashMap<Activity, View>()

  fun updateOptions(options: SentryBuddyOptions) {
    controller.sentryUiLinks = options.sentryUiLinks()
  }

  fun attach(activity: Activity) {
    if (overlays.containsKey(activity)) {
      return
    }
    val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
    val hitBounds = BuddyOverlayHitBounds()
    val container = BuddyOverlayContainer(activity, controller, hitBounds)
    controller.screenScanner = { BuddyScreenScanner.scan(activity, container) }
    container.layoutParams =
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    val composeView = ComposeView(activity)
    composeView.layoutParams =
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
      )
    composeView.setContent { MaterialTheme { SentryBuddyInstalledOverlay(controller, hitBounds) } }
    container.addView(composeView)
    try {
      content.addView(container)
      overlays[activity] = container
    } catch (_: IllegalStateException) {
      content.removeView(container)
    }
  }

  fun detach(activity: Activity) {
    val overlay = overlays.remove(activity) ?: return
    (overlay.parent as? ViewGroup)?.removeView(overlay)
    if (overlays.isEmpty()) {
      controller.screenScanner = null
    }
  }

  fun detachAll() {
    overlays.keys.toList().forEach(::detach)
  }

  fun recordingEvent(text: String) {
    controller.recordTransientEvent(text)
  }
}

@SuppressLint("ViewConstructor")
internal class BuddyOverlayContainer(
  context: Context,
  private val controller: SentryBuddySessionController,
  private val bubbleHitBounds: BuddyOverlayHitBounds,
) : FrameLayout(context) {
  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    val state = controller.state
    if (
      (state is SentryBuddySessionState.Closed || state is SentryBuddySessionState.Recording) &&
        !bubbleHitBounds.contains(event.x, event.y)
    ) {
      return false
    }
    return super.dispatchTouchEvent(event)
  }
}
