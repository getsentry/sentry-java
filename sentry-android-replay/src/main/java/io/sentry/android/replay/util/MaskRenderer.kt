package io.sentry.android.replay.util

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.ImageViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode
import java.io.Closeable
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Shared utility for rendering masks on bitmaps based on view hierarchy. Used by both Session
 * Replay (PixelCopyStrategy) and Screenshot masking.
 */
@SuppressLint("UseKtx")
internal class MaskRenderer : Closeable {

  private companion object {
    private const val MASK_CORNER_RADIUS = 10f
  }

  // Single pixel bitmap for dominant color sampling (averaging the region)
  internal val singlePixelBitmap: Bitmap by
    lazy(NONE) { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
  private val singlePixelBitmapCanvas: Canvas by lazy(NONE) { Canvas(singlePixelBitmap) }
  private val maskingPaint by lazy(NONE) { Paint() }

  /**
   * Renders masks onto the given bitmap based on the view hierarchy.
   *
   * @param bitmap The bitmap to render masks onto (must be mutable)
   * @param viewHierarchy The root node of the view hierarchy
   * @param scaleMatrix Optional matrix for scaling (used by replay for lower resolution)
   * @return List of masked rectangles (for debug overlay)
   */
  fun renderMasks(
    bitmap: Bitmap,
    viewHierarchy: ViewHierarchyNode,
    scaleMatrix: Matrix? = null,
  ): List<Rect> {
    if (bitmap.isRecycled) {
      return emptyList()
    }

    val maskedRects = mutableListOf<Rect>()
    val canvas = Canvas(bitmap)

    scaleMatrix?.let { canvas.setMatrix(it) }

    viewHierarchy.traverse { node ->
      if (node.shouldMask && node.width > 0 && node.height > 0) {
        node.visibleRect ?: return@traverse false

        val (visibleRects, color) =
          when (node) {
            is ImageViewHierarchyNode -> {
              listOf(node.visibleRect) to
                dominantColorForRect(bitmap, node.visibleRect, scaleMatrix)
            }
            is TextViewHierarchyNode -> {
              val textColor = node.layout?.dominantTextColor ?: node.dominantColor ?: Color.BLACK
              node.layout.getVisibleRects(node.visibleRect, node.paddingLeft, node.paddingTop) to
                textColor
            }
            else -> {
              listOf(node.visibleRect) to Color.BLACK
            }
          }

        maskingPaint.color = color
        visibleRects.forEach { rect ->
          canvas.drawRoundRect(RectF(rect), MASK_CORNER_RADIUS, MASK_CORNER_RADIUS, maskingPaint)
        }
        maskedRects.addAll(visibleRects)
      }
      return@traverse true
    }

    return maskedRects
  }

  /**
   * Samples the dominant color from a region of the bitmap by scaling the region down to a single
   * pixel (averaging all colors in the region).
   */
  private fun dominantColorForRect(bitmap: Bitmap, rect: Rect, scaleMatrix: Matrix? = null): Int {
    if (bitmap.isRecycled || singlePixelBitmap.isRecycled) {
      return Color.BLACK
    }

    val visibleRect = Rect(rect)
    val visibleRectF = RectF(visibleRect)

    // Apply scale matrix if provided (for replay's lower resolution)
    scaleMatrix?.mapRect(visibleRectF)
    visibleRectF.round(visibleRect)

    // Draw the region scaled down to 1x1 pixel (averages the colors)
    singlePixelBitmapCanvas.drawBitmap(bitmap, visibleRect, Rect(0, 0, 1, 1), null)

    // Return the averaged color
    return singlePixelBitmap.getPixel(0, 0)
  }

  /** Releases resources. Call when done with this renderer. */
  override fun close() {
    if (!singlePixelBitmap.isRecycled) {
      singlePixelBitmap.recycle()
    }
  }
}
