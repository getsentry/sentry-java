package io.sentry.android.replay.screenshot

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
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
import kotlin.LazyThreadSafetyMode.NONE

@SuppressLint("UseKtx")
internal class PixelCopyStrategy(
  executorProvider: ExecutorProvider,
  private val screenshotRecorderCallback: ScreenshotRecorderCallback?,
  private val options: SentryOptions,
  private val config: ScreenshotRecorderConfig,
  private val debugOverlayDrawable: DebugOverlayDrawable,
) : ScreenshotStrategy {

  private val executor = executorProvider.getExecutor()
  private val mainLooperHandler = executorProvider.getMainLooperHandler()
  private val screenshot =
    Bitmap.createBitmap(config.recordingWidth, config.recordingHeight, Bitmap.Config.ARGB_8888)
  private val prescaledMatrix by
    lazy(NONE) { Matrix().apply { preScale(config.scaleFactorX, config.scaleFactorY) } }
  private val lastCaptureSuccessful = AtomicBoolean(false)
  private val maskRenderer = MaskRenderer()
  private val contentChanged = AtomicBoolean(false)
  private val isClosed = AtomicBoolean(false)

  @SuppressLint("NewApi")
  override fun capture(root: View) {
    val window = root.phoneWindow
    if (window == null) {
      options.logger.log(DEBUG, "Window is invalid, not capturing screenshot")
      return
    }

    if (isClosed.get()) {
      options.logger.log(DEBUG, "PixelCopyStrategy is closed, not capturing screenshot")
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
            return@request
          }

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
          val viewHierarchy = ViewHierarchyNode.fromView(root, null, 0, options.sessionReplay)
          root.traverse(viewHierarchy, options.sessionReplay, options.logger)

          executor.submit(
            ReplayRunnable("screenshot_recorder.mask") {
              if (isClosed.get() || screenshot.isRecycled) {
                options.logger.log(DEBUG, "PixelCopyStrategy is closed, skipping masking")
                return@ReplayRunnable
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
            }
          )
        },
        mainLooperHandler.handler,
      )
    } catch (e: Throwable) {
      options.logger.log(WARNING, "Failed to capture replay recording", e)
      lastCaptureSuccessful.set(false)
    }
  }

  override fun onContentChanged() {
    contentChanged.set(true)
  }

  override fun lastCaptureSuccessful(): Boolean {
    return lastCaptureSuccessful.get()
  }

  override fun emitLastScreenshot() {
    if (lastCaptureSuccessful() && !screenshot.isRecycled) {
      screenshotRecorderCallback?.onScreenshotRecorded(screenshot)
    }
  }

  override fun close() {
    isClosed.set(true)
    executor.submit(
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
    )
  }
}
