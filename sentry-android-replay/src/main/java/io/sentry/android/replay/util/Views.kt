package io.sentry.android.replay.util

import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.VectorDrawable
import android.text.Layout
import android.view.View

/**
 * Adapted copy of AccessibilityNodeInfo from https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/view/View.java;l=10718
 */
internal fun View.isVisibleToUser(): Pair<Boolean, Rect?> {
    if (isAttachedToWindow) {
        // Attached to invisible window means this view is not visible.
        if (windowVisibility != View.VISIBLE) {
            return false to null
        }
        // An invisible predecessor or one with alpha zero means
        // that this view is not visible to the user.
        var current: Any = this
        while (current is View) {
            val view = current
            // We have attach info so this view is attached and there is no
            // need to check whether we reach to ViewRootImpl on the way up.
            if (view.alpha <= 0 || view.transitionAlpha <= 0 || view.visibility != View.VISIBLE) {
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

internal fun Drawable?.isRedactable(): Boolean {
    // TODO: maybe find a way how to check if the drawable is coming from the apk or loaded from network
    // TODO: otherwise maybe check for the bitmap size and don't redact those that take a lot of height (e.g. a background of a whatsapp chat)
    return when (this) {
        is InsetDrawable, is ColorDrawable, is VectorDrawable, is GradientDrawable -> false
        is BitmapDrawable -> !bitmap.isRecycled && bitmap.height > 10 && bitmap.width > 10
        else -> true
    }
}

internal fun Layout?.getVisibleRects(globalRect: Rect, paddingLeft: Int, paddingTop: Int): List<Rect> {
    if (this == null) {
        return listOf(globalRect)
    }
    // TODO: actually not sure - maybe the old way is not that bad, because multiline rects can seem noisy
    val rects = mutableListOf<Rect>()
    for (i in 0 until lineCount) {
        val lineStart = getPrimaryHorizontal(getLineStart(i)).toInt()
        val ellipsisCount = getEllipsisCount(i)
        val lineEnd = getPrimaryHorizontal(getLineVisibleEnd(i) - ellipsisCount + if (ellipsisCount > 0) 1 else 0).toInt()
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
