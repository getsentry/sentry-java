package io.sentry.android.buddy

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

internal sealed class BuddyScreenScanState {
  data object Hidden : BuddyScreenScanState()

  data class Scanning(val result: BuddyScreenScanResult, val startedAtMs: Long) :
    BuddyScreenScanState()

  data class Results(val result: BuddyScreenScanResult) : BuddyScreenScanState()
}

internal data class BuddyScreenScanResult(
  val screenName: String,
  val bounds: List<BuddyScreenScanBounds>,
  val instrumentation: List<BuddyScreenInstrumentationItem>,
)

internal data class BuddyScreenScanBounds(
  val label: String,
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  val width: Float
    get() = right - left

  val height: Float
    get() = bottom - top
}

internal enum class BuddyInstrumentationStatus {
  ENABLED,
  WARNING,
  MISSING,
}

internal data class BuddyScreenInstrumentationItem(
  val label: String,
  val value: String,
  val status: BuddyInstrumentationStatus,
)

internal object BuddyScreenScanner {
  fun scan(activity: Activity, buddyOverlay: View): BuddyScreenScanResult {
    val content = activity.findViewById<ViewGroup>(android.R.id.content)
    val overlayOrigin = IntArray(2)
    buddyOverlay.getLocationOnScreen(overlayOrigin)
    val bounds = mutableListOf<BuddyScreenScanBounds>()
    content?.let { collectBounds(it, buddyOverlay, overlayOrigin, bounds) }
    return BuddyScreenScanResult(
      screenName = activity.javaClass.simpleName,
      bounds = bounds.dedupeBounds().take(MAX_SCAN_BOUNDS),
      instrumentation = emptyList(),
    )
  }

  private fun collectBounds(
    view: View,
    buddyOverlay: View,
    overlayOrigin: IntArray,
    bounds: MutableList<BuddyScreenScanBounds>,
  ) {
    if (view === buddyOverlay || !view.isShown) {
      return
    }

    val rect = Rect()
    if (
      view.getGlobalVisibleRect(rect) &&
        rect.width() >= MIN_SCAN_SIZE &&
        rect.height() >= MIN_SCAN_SIZE
    ) {
      bounds +=
        BuddyScreenScanBounds(
          label = view.scanLabel(),
          left = (rect.left - overlayOrigin[0]).toFloat(),
          top = (rect.top - overlayOrigin[1]).toFloat(),
          right = (rect.right - overlayOrigin[0]).toFloat(),
          bottom = (rect.bottom - overlayOrigin[1]).toFloat(),
        )
    }

    if (view is ViewGroup) {
      for (index in 0 until view.childCount) {
        collectBounds(view.getChildAt(index), buddyOverlay, overlayOrigin, bounds)
      }
    }
  }

  private fun View.scanLabel(): String {
    contentDescription
      ?.toString()
      ?.takeIf { it.isNotBlank() }
      ?.let {
        return it
      }
    if (id != View.NO_ID) {
      runCatching { resources.getResourceEntryName(id) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.let {
          return it
        }
    }
    return javaClass.simpleName
  }

  private fun List<BuddyScreenScanBounds>.dedupeBounds(): List<BuddyScreenScanBounds> {
    val seen = mutableSetOf<String>()
    return filter { bounds ->
      val key = "${bounds.left}:${bounds.top}:${bounds.right}:${bounds.bottom}"
      seen.add(key)
    }
  }

  private const val MIN_SCAN_SIZE = 32
  private const val MAX_SCAN_BOUNDS = 18
}
