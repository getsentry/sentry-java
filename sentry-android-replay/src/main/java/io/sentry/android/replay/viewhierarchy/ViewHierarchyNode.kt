package io.sentry.android.replay.viewhierarchy

import android.annotation.TargetApi
import android.graphics.Rect
import android.text.Layout
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import io.sentry.SentryOptions
import io.sentry.android.replay.util.isRedactable
import io.sentry.android.replay.util.isVisibleToUser

@TargetApi(26)
sealed class ViewHierarchyNode(
    val x: Float,
    val y: Float,
    val width: Int,
    val height: Int,
    val elevation: Float,
    val shouldRedact: Boolean = false,
    val visibleRect: Rect? = null
) {
    var children: List<ViewHierarchyNode>? = null

    class GenericViewHierarchyNode(
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        elevation: Float,
        shouldRedact: Boolean = false,
        visibleRect: Rect? = null
    ): ViewHierarchyNode(x, y, width, height, elevation, shouldRedact, visibleRect)

    class TextViewHierarchyNode(
        val layout: Layout? = null,
        val dominantColor: Int? = null,
        val paddingLeft: Int = 0,
        val paddingTop: Int = 0,
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        elevation: Float,
        shouldRedact: Boolean = false,
        visibleRect: Rect? = null
    ) : ViewHierarchyNode(x, y, width, height, elevation, shouldRedact, visibleRect)

    class ImageViewHierarchyNode(
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        elevation: Float,
        shouldRedact: Boolean = false,
        visibleRect: Rect? = null
    ) : ViewHierarchyNode(x, y, width, height, elevation, shouldRedact, visibleRect)

    companion object {

        private fun Int.toOpaque() = this or 0xFF000000.toInt()

        fun fromView(view: View, options: SentryOptions): ViewHierarchyNode {
            when {
                view is TextView && options.experimental.sessionReplay.redactAllText -> {
                    val (isVisible, visibleRect) = view.isVisibleToUser()
                    return TextViewHierarchyNode(
                        layout = view.layout,
                        dominantColor = view.currentTextColor.toOpaque(),
                        paddingLeft = view.totalPaddingLeft,
                        paddingTop = view.totalPaddingTop,
                        x = view.x,
                        y = view.y,
                        width = view.width,
                        height = view.height,
                        elevation = view.elevation,
                        shouldRedact = isVisible,
                        visibleRect = visibleRect
                    )
                }

                view is ImageView && options.experimental.sessionReplay.redactAllImages -> {
                    val (isVisible, visibleRect) = view.isVisibleToUser()
                    return ImageViewHierarchyNode(
                        x = view.x,
                        y = view.y,
                        width = view.width,
                        height = view.height,
                        elevation = view.elevation,
                        shouldRedact = isVisible && view.drawable?.isRedactable() == true,
                        visibleRect = visibleRect
                    )
                }
            }
            return GenericViewHierarchyNode(
                view.x,
                view.y,
                view.width,
                view.height,
                view.elevation
            )
        }
    }
}
