package io.sentry.android.replay.viewhierarchy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.VectorDrawable
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.TextView

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

    fun adjustAlpha(color: Int): Int {
      val alpha = 255
      val red = Color.red(color)
      val green = Color.green(color)
      val blue = Color.blue(color)
      return Color.argb(alpha, red, green, blue)
    }

    fun fromView(view: View): ViewHierarchyNode {
      var shouldRedact = false
      var dominantColor: Int? = null
      var rect: Rect? = null
      when (view) {
        is TextView -> {
          val nodeInfo = AccessibilityNodeInfo()
          view.onInitializeAccessibilityNodeInfo(nodeInfo)
          shouldRedact = nodeInfo.isVisibleToUser
          if (shouldRedact) {
            val bounds = Rect()
            val text = view.text.toString()
            view.paint.getTextBounds(text, 0, text.length, bounds)
            dominantColor = adjustAlpha(view.currentTextColor)
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
            rect.left += textStart + view.paddingStart
            rect.right = rect.left + (textEnd - textStart)
          }
        }

        is ImageView -> {
          shouldRedact = isVisible(view) && (view.drawable?.isRedactable() ?: false)
          if (shouldRedact) {
            dominantColor = adjustAlpha((view.drawable?.pickDominantColor() ?: Color.BLACK))
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
      return when (this) {
        is InsetDrawable, is ColorDrawable, is VectorDrawable, is GradientDrawable -> false
        is BitmapDrawable -> !bitmap.isRecycled && bitmap.height > 0 && bitmap.width > 0
        else -> true
      }
    }

    private fun Drawable.pickDominantColor(): Int {
      // TODO: pick default color based on dark/light default theme
      return when (this) {
        is BitmapDrawable -> {
          val newBitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
          val color = newBitmap.getPixel(0, 0)
          newBitmap.recycle()
          color
        }

        else -> {
          if (intrinsicHeight > 0 && intrinsicWidth > 0) {
            val bmp =
              Bitmap.createBitmap(this.intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            try {
              draw(canvas)
              val newBitmap = Bitmap.createScaledBitmap(bmp, 1, 1, true)
              val color = newBitmap.getPixel(0, 0)
              newBitmap.recycle()
              bmp.recycle()
              color
            } catch (e: Throwable) {
              Color.BLACK
            }
          } else {
            Color.BLACK
          }
        }
      }
    }
  }
}
