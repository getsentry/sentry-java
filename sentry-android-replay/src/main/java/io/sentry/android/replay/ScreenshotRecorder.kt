package io.sentry.android.replay

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.WARNING
import io.sentry.SentryOptions
import io.sentry.SentryReplayOptions
import io.sentry.android.replay.screenshot.CanvasStrategy
import io.sentry.android.replay.screenshot.ScreenshotStrategy
import io.sentry.android.replay.util.DebugOverlayDrawable
import io.sentry.android.replay.util.addOnDrawListenerSafe
import io.sentry.android.replay.util.removeOnDrawListenerSafe
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@SuppressLint("UseKtx")
@TargetApi(26)
internal class ScreenshotRecorder(
  val config: ScreenshotRecorderConfig,
  val options: SentryOptions,
  recorder: ScheduledExecutorService,
  screenshotRecorderCallback: ScreenshotRecorderCallback?,
) : ViewTreeObserver.OnDrawListener {
  private var rootView: WeakReference<View>? = null
  private val isCapturing = AtomicBoolean(true)

  private val debugOverlayDrawable = DebugOverlayDrawable()
  private val contentChanged = AtomicBoolean(false)

  private val screenshotStrategy: ScreenshotStrategy =
    CanvasStrategy(recorder, screenshotRecorderCallback, options, config)

  //    PixelCopyStrategy(
  //    recorder,
  //    mainLooperHandler,
  //    screenshotRecorderCallback,
  //    options, config)

  fun capture() {
    if (options.sessionReplay.isDebug) {
      options.logger.log(DEBUG, "Capturing screenshot, isCapturing: %s", isCapturing.get())
    }
    if (!isCapturing.get()) {
      if (options.sessionReplay.isDebug) {
        options.logger.log(DEBUG, "ScreenshotRecorder is paused, not capturing screenshot")
      }
      return
    }

    if (options.sessionReplay.isDebug) {
      options.logger.log(
        DEBUG,
        "Capturing screenshot, contentChanged: %s, lastCaptureSuccessful: %s",
        contentChanged.get(),
        screenshotStrategy.lastCaptureSuccessful(),
      )
    }

    if (!contentChanged.get()) {
      screenshotStrategy.emitLastScreenshot()
      return
    }

    val root = rootView?.get()
    if (root == null || root.width <= 0 || root.height <= 0 || !root.isShown) {
      options.logger.log(DEBUG, "Root view is invalid, not capturing screenshot")
      return
    }

    val window = root.phoneWindow
    if (window == null) {
      options.logger.log(DEBUG, "Window is invalid, not capturing screenshot")
      return
    }

    try {
      contentChanged.set(false)
      val start = SystemClock.uptimeMillis()
      screenshotStrategy.capture(root)
      if (options.sessionReplay.isDebug || true) {
        val duration = SystemClock.uptimeMillis() - start
        options.logger.log(DEBUG, "screenshotStrategy.capture took %d ms", duration)
      }
    } catch (e: Throwable) {
      options.logger.log(WARNING, "Failed to capture replay recording", e)
    }
  }

  override fun onDraw() {
    if (!isCapturing.get()) {
      return
    }
    val root = rootView?.get()
    if (root == null || root.width <= 0 || root.height <= 0 || !root.isShown) {
      options.logger.log(DEBUG, "Root view is invalid, not capturing screenshot")
      return
    }

    contentChanged.set(true)
    screenshotStrategy.onContentChanged()
  }

  fun bind(root: View) {
    // first unbind the current root
    unbind(rootView?.get())
    rootView?.clear()

    // next bind the new root
    rootView = WeakReference(root)
    root.addOnDrawListenerSafe(this)

    // invalidate the flag to capture the first frame after new window is attached
    contentChanged.set(true)
    screenshotStrategy.onContentChanged()
  }

  fun unbind(root: View?) {
    if (options.replayController.isDebugMaskingOverlayEnabled()) {
      root?.overlay?.remove(debugOverlayDrawable)
    }
    root?.removeOnDrawListenerSafe(this)
  }

  fun pause() {
    isCapturing.set(false)
    unbind(rootView?.get())
  }

  fun resume() {
    // can't use bind() as it will invalidate the weakref
    rootView?.get()?.addOnDrawListenerSafe(this)
    isCapturing.set(true)
  }

  fun close() {
    unbind(rootView?.get())
    rootView?.clear()
    screenshotStrategy.close()
    isCapturing.set(false)
  }
}

public data class ScreenshotRecorderConfig(
  val recordingWidth: Int,
  val recordingHeight: Int,
  val scaleFactorX: Float,
  val scaleFactorY: Float,
  val frameRate: Int,
  val bitRate: Int,
) {
  internal constructor(
    scaleFactorX: Float,
    scaleFactorY: Float,
  ) : this(
    recordingWidth = 0,
    recordingHeight = 0,
    scaleFactorX = scaleFactorX,
    scaleFactorY = scaleFactorY,
    frameRate = 0,
    bitRate = 0,
  )

  internal companion object {
    /**
     * Since codec block size is 16, so we have to adjust the width and height to it, otherwise the
     * codec might fail to configure on some devices, see
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/media/java/android/media/MediaCodecInfo.java;l=1999-2001
     */
    private fun Int.adjustToBlockSize(): Int {
      val remainder = this % 16
      return if (remainder <= 8) {
        this - remainder
      } else {
        this + (16 - remainder)
      }
    }

    fun fromSize(
      context: Context,
      sessionReplay: SentryReplayOptions,
      windowWidth: Int,
      windowHeight: Int,
    ): ScreenshotRecorderConfig {
      // use the baseline density of 1x (mdpi)
      val (height, width) =
        ((windowHeight / context.resources.displayMetrics.density) *
            sessionReplay.quality.sizeScale)
          .roundToInt()
          .adjustToBlockSize() to
          ((windowWidth / context.resources.displayMetrics.density) *
              sessionReplay.quality.sizeScale)
            .roundToInt()
            .adjustToBlockSize()

      return ScreenshotRecorderConfig(
        recordingWidth = width,
        recordingHeight = height,
        scaleFactorX = width.toFloat() / windowWidth,
        scaleFactorY = height.toFloat() / windowHeight,
        frameRate = sessionReplay.frameRate,
        bitRate = sessionReplay.quality.bitRate,
      )
    }
  }
}

/**
 * A callback to be invoked when a new screenshot available. Normally, only one of the
 * [onScreenshotRecorded] method overloads should be called by a single recorder, however, it will
 * still work of both are used at the same time.
 */
public interface ScreenshotRecorderCallback {
  /**
   * Called whenever a new frame screenshot is available.
   *
   * @param bitmap a screenshot taken in the form of [android.graphics.Bitmap]
   */
  public fun onScreenshotRecorded(bitmap: Bitmap)

  /**
   * Called whenever a new frame screenshot is available.
   *
   * @param screenshot file containing the frame screenshot
   * @param frameTimestamp the timestamp when the frame screenshot was taken
   */
  public fun onScreenshotRecorded(screenshot: File, frameTimestamp: Long)
}

/** A callback to be invoked when once current window size is determined or changes */
public interface WindowCallback {
  public fun onWindowSizeChanged(width: Int, height: Int)
}
