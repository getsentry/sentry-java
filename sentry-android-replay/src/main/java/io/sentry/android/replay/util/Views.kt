package io.sentry.android.replay.util

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.text.Layout
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import io.sentry.ILogger
import io.sentry.SentryMaskingOptions
import io.sentry.android.replay.viewhierarchy.ComposeViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import java.lang.NullPointerException

/**
 * Recursively traverses the view hierarchy and creates a [ViewHierarchyNode] for each view.
 * Supports Compose view hierarchy as well.
 *
 * @param parentNode The parent node in the view hierarchy
 * @param options The masking configuration to use
 * @param logger Logger for error reporting during Compose traversal
 */
@SuppressLint("UseKtx")
internal fun View.traverse(
  parentNode: ViewHierarchyNode,
  options: SentryMaskingOptions,
  logger: ILogger,
) {
  if (this !is ViewGroup) {
    return
  }

  if (ComposeViewHierarchyNode.fromView(this, parentNode, options, logger)) {
    // if it's a compose view, we can skip the children as they are already traversed in
    // the ComposeViewHierarchyNode.fromView method
    return
  }

  if (this.childCount == 0) {
    return
  }

  val childNodes = ArrayList<ViewHierarchyNode>(this.childCount)
  for (i in 0 until childCount) {
    val child = getChildAt(i)
    if (child != null) {
      val childNode = ViewHierarchyNode.fromView(child, parentNode, indexOfChild(child), options)
      childNodes.add(childNode)
      child.traverse(childNode, options, logger)
    }
  }
  parentNode.children = childNodes
}

/**
 * Adapted copy of AccessibilityNodeInfo from
 * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/view/View.java;l=10718
 */
internal fun View.isVisibleToUser(): Pair<Boolean, Rect?> {
  if (isAttachedToWindow) {
    // Attached to invisible window means this view is not visible.
    if (windowVisibility != View.VISIBLE) {
      return false to null
    }
    // An invisible predecessor or one with alpha zero means
    // that this view is not visible to the user.
    var current: Any? = this
    while (current is View) {
      val view = current
      val transitionAlpha = if (VERSION.SDK_INT >= VERSION_CODES.Q) view.transitionAlpha else 1f
      // We have attach info so this view is attached and there is no
      // need to check whether we reach to ViewRootImpl on the way up.
      if (view.alpha <= 0 || transitionAlpha <= 0 || view.visibility != View.VISIBLE) {
        return false to null
      }
      current = view.parent
    }
    // Check if the view is entirely covered by its predecessors.
    val rect = Rect()
    val offset = Point()
    val isVisible = getGlobalVisibleRect(rect, offset)
    return isVisible to rect
  }
  return false to null
}

@SuppressLint("ObsoleteSdkInt", "UseRequiresApi")
@TargetApi(21)
internal fun Drawable?.isMaskable(): Boolean {
  // TODO: maybe find a way how to check if the drawable is coming from the apk or loaded from
  // network
  // TODO: otherwise maybe check for the bitmap size and don't mask those that take a lot of height
  // (e.g. a background of a whatsapp chat)
  return when (this) {
    is InsetDrawable,
    is ColorDrawable,
    is VectorDrawable,
    is GradientDrawable -> false
    is BitmapDrawable -> {
      val bmp = bitmap ?: return false
      return !bmp.isRecycled && bmp.height > 10 && bmp.width > 10
    }
    else -> true
  }
}

internal fun TextLayout?.getVisibleRects(
  globalRect: Rect,
  paddingLeft: Int,
  paddingTop: Int,
): List<Rect> {
  if (this == null) {
    return listOf(globalRect)
  }

  val rects = mutableListOf<Rect>()
  for (i in 0 until lineCount) {
    val lineStart = getPrimaryHorizontal(i, getLineStart(i)).toInt()
    val ellipsisCount = getEllipsisCount(i)
    val lineVisibleEnd = getLineVisibleEnd(i)
    var lineEnd =
      getPrimaryHorizontal(i, lineVisibleEnd - ellipsisCount + if (ellipsisCount > 0) 1 else 0)
        .toInt()
    if (lineEnd == 0 && lineVisibleEnd > 0) {
      // looks like the case for when emojis are present in text
      lineEnd = getPrimaryHorizontal(i, lineVisibleEnd - 1).toInt() + 1
    }
    val lineTop = getLineTop(i)
    val lineBottom = getLineBottom(i)
    val rect = Rect()
    rect.left = globalRect.left + paddingLeft + lineStart
    rect.right = rect.left + (lineEnd - lineStart)
    rect.top = globalRect.top + paddingTop + lineTop
    rect.bottom = rect.top + (lineBottom - lineTop)

    rects += rect
  }
  return rects
}

/**
 * [TextView.getVerticalOffset] which is used by [TextView.getTotalPaddingTop] may throw an NPE on
 * some devices (Redmi), so we try-catch it specifically for an NPE and then fallback to
 * [TextView.getExtendedPaddingTop]
 */
internal val TextView.totalPaddingTopSafe: Int
  get() =
    try {
      totalPaddingTop
    } catch (e: NullPointerException) {
      extendedPaddingTop
    }

/** Converts an [Int] ARGB color to an opaque color by setting the alpha channel to 255. */
internal fun Int.toOpaque() = this or 0xFF000000.toInt()

internal class AndroidTextLayout(private val layout: Layout) : TextLayout {
  override val lineCount: Int
    get() = layout.lineCount

  override val dominantTextColor: Int?
    get() {
      if (layout.text !is Spanned) return null

      val spans =
        (layout.text as Spanned).getSpans(0, layout.text.length, ForegroundColorSpan::class.java)

      // determine the dominant color by the span with the longest range
      var longestSpan = Int.MIN_VALUE
      var dominantColor: Int? = null
      for (span in spans) {
        val spanStart = (layout.text as Spanned).getSpanStart(span)
        val spanEnd = (layout.text as Spanned).getSpanEnd(span)
        if (spanStart == -1 || spanEnd == -1) {
          // the span is not attached
          continue
        }
        val spanLength = spanEnd - spanStart
        if (spanLength > longestSpan) {
          longestSpan = spanLength
          dominantColor = span.foregroundColor
        }
      }
      return dominantColor?.toOpaque()
    }

  override fun getPrimaryHorizontal(line: Int, offset: Int): Float =
    layout.getPrimaryHorizontal(offset)

  override fun getEllipsisCount(line: Int): Int = layout.getEllipsisCount(line)

  override fun getLineVisibleEnd(line: Int): Int = layout.getLineVisibleEnd(line)

  override fun getLineTop(line: Int): Int = layout.getLineTop(line)

  override fun getLineBottom(line: Int): Int = layout.getLineBottom(line)

  override fun getLineStart(line: Int): Int = layout.getLineStart(line)
}

internal fun View?.addOnDrawListenerSafe(listener: ViewTreeObserver.OnDrawListener) {
  if (this == null || viewTreeObserver == null || !viewTreeObserver.isAlive) {
    return
  }
  try {
    viewTreeObserver.addOnDrawListener(listener)
  } catch (_: IllegalStateException) {
    // viewTreeObserver is already dead
  }
}

internal fun View?.removeOnDrawListenerSafe(listener: ViewTreeObserver.OnDrawListener) {
  if (this == null || viewTreeObserver == null || !viewTreeObserver.isAlive) {
    return
  }
  try {
    viewTreeObserver.removeOnDrawListener(listener)
  } catch (_: IllegalStateException) {
    // viewTreeObserver is already dead
  }
}

internal fun View?.addOnPreDrawListenerSafe(listener: ViewTreeObserver.OnPreDrawListener) {
  if (this == null || viewTreeObserver == null || !viewTreeObserver.isAlive) {
    return
  }
  try {
    viewTreeObserver.addOnPreDrawListener(listener)
  } catch (_: IllegalStateException) {
    // viewTreeObserver is already dead
  }
}

internal fun View?.removeOnPreDrawListenerSafe(listener: ViewTreeObserver.OnPreDrawListener) {
  if (this == null || viewTreeObserver == null || !viewTreeObserver.isAlive) {
    return
  }
  try {
    viewTreeObserver.removeOnPreDrawListener(listener)
  } catch (_: IllegalStateException) {
    // viewTreeObserver is already dead
  }
}

internal fun View.hasSize(): Boolean = width > 0 && height > 0
