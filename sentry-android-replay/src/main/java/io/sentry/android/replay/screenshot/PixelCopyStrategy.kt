package io.sentry.android.replay.screenshot

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.PixelCopy
import android.view.View
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryLevel.WARNING
import io.sentry.SentryOptions
import io.sentry.android.replay.ExecutorProvider
import io.sentry.android.replay.ScreenshotRecorderCallback
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.phoneWindow
import io.sentry.android.replay.util.DebugOverlayDrawable
import io.sentry.android.replay.util.MaskRenderer
import io.sentry.android.replay.util.ReplayRunnable
import io.sentry.android.replay.util.traverse
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.LazyThreadSafetyMode.NONE

@SuppressLint("UseKtx")
internal class PixelCopyStrategy(
  executorProvider: ExecutorProvider,
  private val screenshotRecorderCallback: ScreenshotRecorderCallback?,
  private val options: SentryOptions,
  private val config: ScreenshotRecorderConfig,
  private val debugOverlayDrawable: DebugOverlayDrawable,
  // Lets the strategy re-arm the recorder's contentChanged gate so frames keep being captured
  // when SurfaceViews are present (their redraws don't trigger ViewTreeObserver.OnDrawListener).
  private val markContentChanged: () -> Unit = {},
) : ScreenshotStrategy {

  private companion object {
    /**
     * An unstable capture means the view hierarchy changed while PixelCopy was in flight. Cap
     * skipped unstable captures so continuous animations don't stop replay recording.
     */
    const val MAX_UNSTABLE_CAPTURES_TO_SKIP = 1
  }

  private val executor = executorProvider.getExecutor()
  private val mainLooperHandler = executorProvider.getMainLooperHandler()
  private val screenshot =
    Bitmap.createBitmap(config.recordingWidth, config.recordingHeight, Bitmap.Config.ARGB_8888)
  private val prescaledMatrix by
    lazy(NONE) { Matrix().apply { preScale(config.scaleFactorX, config.scaleFactorY) } }
  private val lastCaptureSuccessful = AtomicBoolean(false)
  private val maskRenderer = MaskRenderer()
  private val contentChanged = AtomicBoolean(false)
  private val unstableCaptures = AtomicInteger(0)
  private val isClosed = AtomicBoolean(false)
  private val frameInFlight = AtomicBoolean(false)
  private val cleanupScheduled = AtomicBoolean(false)
  private val dstOverPaint by
    lazy(NONE) { Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) } }
  private val screenshotCanvas by lazy(NONE) { Canvas(screenshot) }
  private val tmpSrcRect = Rect()
  private val tmpDstRect = RectF()
  private val windowLocation = IntArray(2)
  private val svLocation = IntArray(2)

  private class SurfaceViewCapture(val bitmap: Bitmap, val x: Int, val y: Int)

  @SuppressLint("NewApi")
  override fun capture(root: View) {
    val window = root.phoneWindow
    if (window == null) {
      options.logger.log(DEBUG, "Window is invalid, not capturing screenshot")
      return
    }

    if (!frameInFlight.compareAndSet(false, true)) {
      options.logger.log(DEBUG, "PixelCopyStrategy capture is already in flight, skipping")
      markContentChanged()
      return
    }

    if (isClosed.get()) {
      options.logger.log(DEBUG, "PixelCopyStrategy is closed, not capturing screenshot")
      finishFrame()
      return
    }

    try {
      contentChanged.set(false)
      PixelCopy.request(
        window,
        screenshot,
        { copyResult: Int ->
          if (isClosed.get()) {
            options.logger.log(DEBUG, "PixelCopyStrategy is closed, ignoring capture result")
            finishFrame()
            return@request
          }

          if (copyResult != PixelCopy.SUCCESS) {
            options.logger.log(INFO, "Failed to capture replay recording: %d", copyResult)
            unstableCaptures.set(0)
            lastCaptureSuccessful.set(false)
            finishFrame()
            return@request
          }

          val changedDuringCapture = contentChanged.get()
          if (changedDuringCapture && shouldSkipUnstableCapture()) {
            finishFrame()
            return@request
          }

          // Release the frame gate if anything below throws before we hand work off to the
          // executor — otherwise a single failure wedges captures forever.
          try {
            // TODO: disableAllMasking here and dont traverse?
            val viewHierarchy = ViewHierarchyNode.fromView(root, null, 0, options.sessionReplay)
            val surfaceViewNodes =
              if (options.sessionReplay.isCaptureSurfaceViews) {
                mutableListOf<ViewHierarchyNode.SurfaceViewHierarchyNode>()
              } else {
                null
              }
            root.traverse(viewHierarchy, options.sessionReplay, options.logger, surfaceViewNodes)

            if (surfaceViewNodes.isNullOrEmpty()) {
              val submitted =
                executor.submit(
                  ReplayRunnable("screenshot_recorder.mask") {
                    try {
                      applyMaskingAndNotify(
                        root,
                        viewHierarchy,
                        resetUnstableCaptures = !changedDuringCapture,
                      )
                    } finally {
                      finishFrame()
                    }
                  }
                )
              if (submitted == null) {
                finishFrame()
              }
            } else {
              // Re-arm the recorder's contentChanged gate; SurfaceView redraws don't trigger
              // ViewTreeObserver.OnDrawListener, so we'd otherwise emit the same frame forever.
              markContentChanged()
              captureSurfaceViews(
                root,
                surfaceViewNodes,
                viewHierarchy,
                resetUnstableCaptures = !changedDuringCapture,
              )
            }
          } catch (e: Throwable) {
            options.logger.log(WARNING, "Failed to process replay frame", e)
            finishFrame()
          }
        },
        mainLooperHandler.handler,
      )
    } catch (e: Throwable) {
      options.logger.log(WARNING, "Failed to capture replay recording", e)
      unstableCaptures.set(0)
      lastCaptureSuccessful.set(false)
      finishFrame()
    }
  }

  private fun shouldSkipUnstableCapture(): Boolean {
    if (unstableCaptures.incrementAndGet() <= MAX_UNSTABLE_CAPTURES_TO_SKIP) {
      options.logger.log(INFO, "Failed to determine view hierarchy, not capturing")
      lastCaptureSuccessful.set(false)
      return true
    }
    return false
  }

  private fun applyMaskingAndNotify(
    root: View,
    viewHierarchy: ViewHierarchyNode,
    resetUnstableCaptures: Boolean,
  ) {
    if (isClosed.get() || screenshot.isRecycled) {
      options.logger.log(DEBUG, "PixelCopyStrategy is closed, skipping masking")
      return
    }

    val debugMasks = maskRenderer.renderMasks(screenshot, viewHierarchy, prescaledMatrix)

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
    if (resetUnstableCaptures) {
      unstableCaptures.set(0)
    }
  }

  @SuppressLint("NewApi")
  private fun captureSurfaceViews(
    root: View,
    surfaceViewNodes: List<ViewHierarchyNode.SurfaceViewHierarchyNode>,
    viewHierarchy: ViewHierarchyNode,
    resetUnstableCaptures: Boolean,
  ) {
    // Snapshot the window location into locals so the executor-side compositor reads stable
    // values even if a new capture cycle starts and overwrites the field.
    root.getLocationOnScreen(windowLocation)
    val windowX = windowLocation[0]
    val windowY = windowLocation[1]

    val captures = arrayOfNulls<SurfaceViewCapture>(surfaceViewNodes.size)
    val remaining = AtomicInteger(surfaceViewNodes.size)

    fun onCaptureComplete() {
      if (remaining.decrementAndGet() == 0) {
        compositeSurfaceViewsAndMask(
          root,
          captures,
          viewHierarchy,
          windowX,
          windowY,
          resetUnstableCaptures,
        )
      }
    }

    for ((index, node) in surfaceViewNodes.withIndex()) {
      val surfaceView = node.surfaceViewRef.get()
      // holder.surface can be null before the surface is created — guard against NPE.
      val surface = surfaceView?.holder?.surface
      if (surfaceView == null || surface == null || !surface.isValid) {
        onCaptureComplete()
        continue
      }

      var svBitmap: Bitmap? = null
      try {
        svBitmap =
          Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
        val bitmapToCapture = svBitmap

        surfaceView.getLocationOnScreen(svLocation)
        val capturedX = svLocation[0]
        val capturedY = svLocation[1]

        PixelCopy.request(
          surfaceView,
          bitmapToCapture,
          { copyResult: Int ->
            if (isClosed.get()) {
              bitmapToCapture.recycle()
              // still drive the completion latch so any prior captures get recycled by the
              // composite step's early-return path.
              onCaptureComplete()
              return@request
            }
            if (copyResult == PixelCopy.SUCCESS) {
              captures[index] = SurfaceViewCapture(bitmapToCapture, capturedX, capturedY)
            } else {
              bitmapToCapture.recycle()
              options.logger.log(INFO, "Failed to capture SurfaceView: %d", copyResult)
            }
            onCaptureComplete()
          },
          mainLooperHandler.handler,
        )
        // Ownership transferred to the PixelCopy callback — clear local so catch doesn't
        // double-recycle if the recycle paths above already ran.
        svBitmap = null
      } catch (e: Throwable) {
        options.logger.log(WARNING, "Failed to capture SurfaceView", e)
        svBitmap?.recycle()
        onCaptureComplete()
      }
    }
  }

  private fun compositeSurfaceViewsAndMask(
    root: View,
    captures: Array<SurfaceViewCapture?>,
    viewHierarchy: ViewHierarchyNode,
    windowX: Int,
    windowY: Int,
    resetUnstableCaptures: Boolean,
  ) {
    val submitted =
      executor.submit(
        ReplayRunnable("screenshot_recorder.composite") {
          try {
            if (isClosed.get() || screenshot.isRecycled) {
              options.logger.log(DEBUG, "PixelCopyStrategy is closed, skipping compositing")
              recycleCaptures(captures)
              return@ReplayRunnable
            }

            for (capture in captures) {
              if (capture == null) continue
              if (capture.bitmap.isRecycled) continue

              compositeSurfaceViewInto(
                screenshotCanvas,
                dstOverPaint,
                tmpSrcRect,
                tmpDstRect,
                capture.bitmap,
                capture.x,
                capture.y,
                windowX,
                windowY,
                config.scaleFactorX,
                config.scaleFactorY,
              )
              capture.bitmap.recycle()
            }

            applyMaskingAndNotify(root, viewHierarchy, resetUnstableCaptures)
          } finally {
            finishFrame()
          }
        }
      )
    if (submitted == null) {
      recycleCaptures(captures)
      finishFrame()
    }
  }

  private fun recycleCaptures(captures: Array<SurfaceViewCapture?>) {
    for (capture in captures) {
      if (capture != null && !capture.bitmap.isRecycled) {
        capture.bitmap.recycle()
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
    if (!frameInFlight.get() && lastCaptureSuccessful() && !screenshot.isRecycled) {
      screenshotRecorderCallback?.onScreenshotRecorded(screenshot)
    }
  }

  override fun close() {
    isClosed.set(true)
    unstableCaptures.set(0)
    if (!frameInFlight.get()) {
      scheduleCleanup()
    }
  }

  private fun finishFrame() {
    frameInFlight.set(false)
    if (isClosed.get()) {
      scheduleCleanup()
    }
  }

  private fun scheduleCleanup() {
    if (!cleanupScheduled.compareAndSet(false, true)) {
      return
    }
    val cleanup =
      ReplayRunnable(
        "PixelCopyStrategy.close",
        {
          if (!screenshot.isRecycled) {
            synchronized(screenshot) {
              if (!screenshot.isRecycled) {
                screenshot.recycle()
              }
            }
          }
          maskRenderer.close()
        },
      )
    // close() typically runs after ReplayIntegration has shut down the executor, so submit may
    // return null. Fall back to running cleanup inline so the bitmap + mask renderer are freed.
    if (executor.submit(cleanup) == null) {
      cleanup.run()
    }
  }
}

/**
 * Composites [sourceBitmap] (a SurfaceView capture) onto [destCanvas] (wrapping the recording
 * screenshot) using [destPaint] (expected to have DST_OVER xfermode), so the SurfaceView content
 * draws _behind_ existing Window content — filling the transparent holes the Window PixelCopy
 * leaves where SurfaceViews are.
 *
 * Extracted for testability — the compositing is pure drawing logic that can be driven with
 * hand-built bitmaps, while the surrounding [PixelCopyStrategy.captureSurfaceViews] flow depends on
 * a real SurfaceView producer that Robolectric cannot provide.
 */
internal fun compositeSurfaceViewInto(
  destCanvas: Canvas,
  destPaint: Paint,
  tmpSrc: Rect,
  tmpDst: RectF,
  sourceBitmap: Bitmap,
  sourceX: Int,
  sourceY: Int,
  windowX: Int,
  windowY: Int,
  scaleFactorX: Float,
  scaleFactorY: Float,
) {
  val left = (sourceX - windowX) * scaleFactorX
  val top = (sourceY - windowY) * scaleFactorY
  tmpSrc.set(0, 0, sourceBitmap.width, sourceBitmap.height)
  tmpDst.set(
    left,
    top,
    left + sourceBitmap.width * scaleFactorX,
    top + sourceBitmap.height * scaleFactorY,
  )
  destCanvas.drawBitmap(sourceBitmap, tmpSrc, tmpDst, destPaint)
}
