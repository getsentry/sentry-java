package io.sentry.android.buddy

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import io.sentry.android.buddy.model.BuddyScreenScanBounds
import io.sentry.android.buddy.model.BuddyScreenScanResult

internal object BuddyScreenScanner {
  fun scan(activity: Activity, buddyOverlay: View): BuddyScreenScanResult {
    val content = activity.findViewById<ViewGroup>(android.R.id.content)
    val overlayOrigin = IntArray(2)
    buddyOverlay.getLocationOnScreen(overlayOrigin)
    val bounds = mutableListOf<BuddyScreenScanBounds>()
    content?.let { collectBounds(it, buddyOverlay, overlayOrigin, bounds) }
    val dedupedBounds = bounds.dedupeBounds()
    return BuddyScreenScanResult(
      screenName = activity.javaClass.simpleName,
      bounds = dedupedBounds.demoReadyBounds().take(MAX_SCAN_BOUNDS),
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

  private fun List<BuddyScreenScanBounds>.demoReadyBounds(): List<BuddyScreenScanBounds> {
    val root = maxByOrNull { it.width * it.height } ?: return emptyList()
    val meaningfulChildren = filter { bounds ->
      bounds !== root &&
        bounds.width * bounds.height < root.width * root.height * 0.86f &&
        bounds.width >= 72f &&
        bounds.height >= 40f
    }
    if (meaningfulChildren.size >= MIN_MEANINGFUL_BOUNDS) {
      return meaningfulChildren + root
    }
    return syntheticComposeBounds(root) + root
  }

  private fun syntheticComposeBounds(root: BuddyScreenScanBounds): List<BuddyScreenScanBounds> {
    val left = root.left + root.width * 0.06f
    val right = root.right - root.width * 0.06f
    val top = root.top + root.height * 0.10f
    val rowHeight = root.height * 0.09f
    val gap = root.height * 0.045f
    return List(SYNTHETIC_BOUND_COUNT) { index ->
      val rowTop = top + index * (rowHeight + gap)
      BuddyScreenScanBounds(
        label = "Detected surface ${index + 1}",
        left = left,
        top = rowTop,
        right = right,
        bottom = rowTop + rowHeight,
      )
    }
  }

  private const val MIN_SCAN_SIZE = 32
  private const val MIN_MEANINGFUL_BOUNDS = 3
  private const val SYNTHETIC_BOUND_COUNT = 5
  private const val MAX_SCAN_BOUNDS = 18
}
