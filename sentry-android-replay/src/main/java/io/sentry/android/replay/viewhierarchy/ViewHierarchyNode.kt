package io.sentry.android.replay.viewhierarchy

import android.annotation.TargetApi
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.TextView
import io.sentry.SentryOptions

// TODO: merge with ViewHierarchyNode from sentry-core maybe?
@TargetApi(26)
data class ViewHierarchyNode(
    val x: Float,
    val y: Float,
    val width: Int,
    val height: Int,
    val shouldRedact: Boolean = false,
    val dominantColor: Int? = null,
    val visibleRect: Rect? = null
) {

    var children: List<ViewHierarchyNode>? = null

    companion object {

        private fun isVisible(view: View?): Boolean {
            if (view == null || !view.isShown) {
                return false
            }
            val actualPosition = Rect()
            view.getGlobalVisibleRect(actualPosition)
            val screen = Rect(
                0,
                0,
                view.context.resources.displayMetrics.widthPixels,
                view.context.resources.displayMetrics.heightPixels
            )
            return actualPosition.intersects(screen.left, screen.top, screen.right, screen.bottom)
        }

        // TODO: check if this works on RN
        private fun Int.toOpaque() = this or 0xFF000000.toInt()

        fun fromView(view: View, options: SentryOptions): ViewHierarchyNode {
            // TODO: Extract redacting into its own class/function
            // TODO: extract redacting into a separate thread?
            var shouldRedact = false
            var dominantColor: Int? = null
            var rect: Rect? = null
            when {
                view is TextView && options.experimental.sessionReplay.redactAllText -> {
                    // TODO: API level check
                    // TODO: perhaps this is heavy, might reconsider
                    val nodeInfo = if (VERSION.SDK_INT >= VERSION_CODES.R) {
                        AccessibilityNodeInfo()
                    } else {
                        AccessibilityNodeInfo.obtain()
                    }
                    view.onInitializeAccessibilityNodeInfo(nodeInfo)
                    shouldRedact = nodeInfo.isVisibleToUser
                    nodeInfo.recycle()
                    if (shouldRedact) {
                        val bounds = Rect()
                        val text = view.text.toString()
                        view.paint.getTextBounds(text, 0, text.length, bounds)
                        dominantColor = view.currentTextColor.toOpaque()
                        rect = Rect()
                        view.getGlobalVisibleRect(rect)

                        var textEnd = Int.MIN_VALUE
                        var textStart = Int.MAX_VALUE
                        if (view.layout != null) {
                            for (i in 0 until view.layout.lineCount) {
                                val min = view.layout.getLineStart(i)
                                val minPosition = view.layout.getPrimaryHorizontal(min).toInt()
                                val max = view.layout.getLineVisibleEnd(i)
                                val maxPosition = view.layout.getPrimaryHorizontal(max).toInt()
                                if (minPosition < textStart) {
                                    textStart = minPosition
                                }
                                if (maxPosition > textEnd) {
                                    textEnd = maxPosition
                                }
                            }
                        } else {
                            textEnd = rect.right - rect.left
                            textStart = 0
                        }
                        // TODO: support known 3rd-party widgets like MaterialButton with an icon
                        // TODO: also calculate height properly based on text bounds
                        rect.left += textStart + view.paddingStart
                        rect.right = rect.left + (textEnd - textStart)
                    }
                }

                view is ImageView && options.experimental.sessionReplay.redactAllImages -> {
                    shouldRedact = isVisible(view) && (view.drawable?.isRedactable() ?: false)
                    if (shouldRedact) {
                        rect = Rect()
                        view.getGlobalVisibleRect(rect)
                    }
                }
            }
            return ViewHierarchyNode(
                view.x,
                view.y,
                view.width,
                view.height,
                shouldRedact,
                dominantColor,
                rect
            )
        }

        private fun Drawable.isRedactable(): Boolean {
            // TODO: maybe find a way how to check if the drawable is coming from the apk or loaded from network
            // TODO: otherwise maybe check for the bitmap size and don't redact those that take a lot of height (e.g. a background of a whatsapp chat)
            return when (this) {
                is InsetDrawable, is ColorDrawable, is VectorDrawable, is GradientDrawable -> false
                is BitmapDrawable -> !bitmap.isRecycled && bitmap.height > 10 && bitmap.width > 10
                else -> true
            }
        }
    }
}
