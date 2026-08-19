package io.sentry.android.buddy.ui.overlay

import android.graphics.Rect
import kotlin.math.roundToInt

internal class BuddyOverlayHitBounds {
  private val lock = Any()
  private var bounds: Rect? = null

  fun update(left: Int, top: Int, right: Int, bottom: Int) {
    synchronized(lock) { bounds = Rect(left, top, right, bottom) }
  }

  fun contains(x: Float, y: Float): Boolean =
    synchronized(lock) { bounds?.contains(x.roundToInt(), y.roundToInt()) == true }
}
