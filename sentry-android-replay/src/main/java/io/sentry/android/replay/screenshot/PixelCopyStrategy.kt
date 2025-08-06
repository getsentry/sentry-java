package io.sentry.android.replay.screenshot

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.PixelCopy
import android.view.View
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryLevel.WARNING
import io.sentry.SentryOptions
import io.sentry.android.replay.ScreenshotRecorderCallback
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.phoneWindow
import io.sentry.android.replay.util.DebugOverlayDrawable
import io.sentry.android.replay.util.MainLooperHandler
import io.sentry.android.replay.util.getVisibleRects
import io.sentry.android.replay.util.submitSafely
import io.sentry.android.replay.util.traverse
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.ImageViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode.NONE

@SuppressLint("UseKtx")
internal class PixelCopyStrategy(
  private val executor: ScheduledExecutorService,
  private val mainLooperHandler: MainLooperHandler,
  private val screenshotRecorderCallback: ScreenshotRecorderCallback?,
  private val options: SentryOptions,
  private val config: ScreenshotRecorderConfig,
  private val debugOverlayDrawable: DebugOverlayDrawable,
) : ScreenshotStrategy {

  private val singlePixelBitmap: Bitmap by
    lazy(NONE) { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
  private val screenshot =
    Bitmap.createBitmap(config.recordingWidth, config.recordingHeight, Bitmap.Config.ARGB_8888)
  private val singlePixelBitmapCanvas: Canvas by lazy(NONE) { Canvas(singlePixelBitmap) }
  private val prescaledMatrix by
    lazy(NONE) { Matrix().apply { preScale(config.scaleFactorX, config.scaleFactorY) } }
  private val lastCaptureSuccessful = AtomicBoolean(false)
  private val maskingPaint by lazy(NONE) { Paint() }
  private val contentChanged = AtomicBoolean(false)

  @SuppressLint("NewApi")
  override fun capture(root: View) {
    contentChanged.set(false)

    val window = root.phoneWindow
    if (window == null) {
      options.logger.log(DEBUG, "Window is invalid, not capturing screenshot")
      return
    }

    // postAtFrontOfQueue to ensure the view hierarchy and bitmap are ase close in-sync as possible
    mainLooperHandler.post {
      try {
        contentChanged.set(false)
        PixelCopy.request(
          window,
          screenshot,
          { copyResult: Int ->
            if (copyResult != PixelCopy.SUCCESS) {
              options.logger.log(INFO, "Failed to capture replay recording: %d", copyResult)
              lastCaptureSuccessful.set(false)
              return@request
            }

            // TODO: handle animations with heuristics (e.g. if we fall under this condition 2 times
            // in a row, we should capture)
            if (contentChanged.get()) {
              options.logger.log(INFO, "Failed to determine view hierarchy, not capturing")
              lastCaptureSuccessful.set(false)
              return@request
            }

            // TODO: disableAllMasking here and dont traverse?
            val viewHierarchy = ViewHierarchyNode.fromView(root, null, 0, options)
            root.traverse(viewHierarchy, options)

            executor.submitSafely(options, "screenshot_recorder.mask") {
              val debugMasks = mutableListOf<Rect>()

              val canvas = Canvas(screenshot)
              canvas.setMatrix(prescaledMatrix)
              viewHierarchy.traverse { node ->
                if (node.shouldMask && (node.width > 0 && node.height > 0)) {
                  node.visibleRect ?: return@traverse false

                  // TODO: investigate why it returns true on RN when it shouldn't
                  //                                    if (viewHierarchy.isObscured(node)) {
                  //                                        return@traverse true
                  //                                    }

                  val (visibleRects, color) =
                    when (node) {
                      is ImageViewHierarchyNode -> {
                        listOf(node.visibleRect) to
                          screenshot.dominantColorForRect(node.visibleRect)
                      }

                      is TextViewHierarchyNode -> {
                        val textColor =
                          node.layout?.dominantTextColor ?: node.dominantColor ?: Color.BLACK
                        node.layout.getVisibleRects(
                          node.visibleRect,
                          node.paddingLeft,
                          node.paddingTop,
                        ) to textColor
                      }

                      else -> {
                        listOf(node.visibleRect) to Color.BLACK
                      }
                    }

                  maskingPaint.setColor(color)
                  visibleRects.forEach { rect ->
                    canvas.drawRoundRect(RectF(rect), 10f, 10f, maskingPaint)
                  }
                  if (options.replayController.isDebugMaskingOverlayEnabled()) {
                    debugMasks.addAll(visibleRects)
                  }
                }
                return@traverse true
              }

              if (options.replayController.isDebugMaskingOverlayEnabled()) {
                mainLooperHandler.post {
                  if (debugOverlayDrawable.callback == null) {
                    root.overlay.add(debugOverlayDrawable)
                  }
                  debugOverlayDrawable.updateMasks(debugMasks)
                  root.postInvalidate()
                }
              }
              screenshotRecorderCallback?.onScreenshotRecorded(screenshot)
              lastCaptureSuccessful.set(true)
              contentChanged.set(false)
            }
          },
          mainLooperHandler.handler,
        )
      } catch (e: Throwable) {
        options.logger.log(WARNING, "Failed to capture replay recording", e)
        lastCaptureSuccessful.set(false)
      }
    }
  }

  override fun onContentChanged() {
    contentChanged.set(true)
  }

  override fun lastCaptureSuccessful(): Boolean {
    return lastCaptureSuccessful.get()
  }

  override fun emitLastScreenshot() {
    if (lastCaptureSuccessful()) {
      screenshotRecorderCallback?.onScreenshotRecorded(screenshot)
    }
  }

  override fun close() {
    if (!screenshot.isRecycled) {
      screenshot.recycle()
    }
  }

  private fun Bitmap.dominantColorForRect(rect: Rect): Int {
    // TODO: maybe this ceremony can be just simplified to
    // TODO: multiplying the visibleRect by the prescaledMatrix
    val visibleRect = Rect(rect)
    val visibleRectF = RectF(visibleRect)

    // since we take screenshot with lower scale, we also
    // have to apply the same scale to the visibleRect to get the
    // correct screenshot part to determine the dominant color
    prescaledMatrix.mapRect(visibleRectF)
    // round it back to integer values, because drawBitmap below accepts Rect only
    visibleRectF.round(visibleRect)
    // draw part of the screenshot (visibleRect) to a single pixel bitmap
    singlePixelBitmapCanvas.drawBitmap(this, visibleRect, Rect(0, 0, 1, 1), null)
    // get the pixel color (= dominant color)
    return singlePixelBitmap.getPixel(0, 0)
  }
}
