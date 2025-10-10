package io.sentry.android.replay

import android.annotation.TargetApi
import android.graphics.Point
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import android.view.ViewTreeObserver
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryLevel.WARNING
import io.sentry.SentryOptions
import io.sentry.android.replay.util.MainLooperHandler
import io.sentry.android.replay.util.addOnPreDrawListenerSafe
import io.sentry.android.replay.util.hasSize
import io.sentry.android.replay.util.removeOnPreDrawListenerSafe
import io.sentry.util.AutoClosableReentrantLock
import java.lang.ref.WeakReference
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean

@TargetApi(26)
internal class WindowRecorder(
  private val options: SentryOptions,
  private val screenshotRecorderCallback: ScreenshotRecorderCallback? = null,
  private val windowCallback: WindowCallback,
  private val mainLooperHandler: MainLooperHandler,
  private val replayExecutor: ScheduledExecutorService,
) : Recorder, OnRootViewsChangedListener, ExecutorProvider {

  private val isRecording = AtomicBoolean(false)
  private val rootViews = ArrayList<WeakReference<View>>()
  private var lastKnownWindowSize: Point = Point()
  private val rootViewsLock = AutoClosableReentrantLock()
  private val capturerLock = AutoClosableReentrantLock()
  private val backgroundProcessingHandlerLock = AutoClosableReentrantLock()

  @Volatile private var capturer: Capturer? = null

  @Volatile private var backgroundProcessingHandlerThread: HandlerThread? = null
  @Volatile private var backgroundProcessingHandler: Handler? = null

  private class Capturer(
    private val options: SentryOptions,
    private val mainLooperHandler: MainLooperHandler,
  ) : Runnable {

    var recorder: ScreenshotRecorder? = null
    var config: ScreenshotRecorderConfig? = null
    private val isRecording = AtomicBoolean(true)

    fun resume() {
      if (options.sessionReplay.isDebug) {
        options.logger.log(DEBUG, "Resuming the capture runnable.")
      }
      recorder?.resume()
      isRecording.getAndSet(true)
      // Remove any existing callbacks to prevent concurrent capture loops
      mainLooperHandler.removeCallbacks(this)
      val posted = mainLooperHandler.post(this)
      if (!posted) {
        options.logger.log(
          WARNING,
          "Failed to post the capture runnable, main looper is not ready.",
        )
      }
    }

    fun pause() {
      recorder?.pause()
      isRecording.getAndSet(false)
    }

    fun stop() {
      recorder?.close()
      recorder = null
      isRecording.getAndSet(false)
    }

    override fun run() {
      // protection against the case where the capture is executed after the recording has stopped
      if (!isRecording.get()) {
        if (options.sessionReplay.isDebug) {
          options.logger.log(DEBUG, "Not capturing frames, recording is not running.")
        }
        return
      }

      try {
        if (options.sessionReplay.isDebug) {
          options.logger.log(DEBUG, "Capturing a frame.")
        }
        recorder?.capture()
      } catch (e: Throwable) {
        options.logger.log(ERROR, "Failed to capture a frame", e)
      }

      if (options.sessionReplay.isDebug) {
        options.logger.log(
          DEBUG,
          "Posting the capture runnable again, frame rate is ${config?.frameRate ?: 1} fps.",
        )
      }
      val posted = mainLooperHandler.postDelayed(this, 1000L / (config?.frameRate ?: 1))
      if (!posted) {
        options.logger.log(
          WARNING,
          "Failed to post the capture runnable, main looper is shutting down.",
        )
      }
    }
  }

  override fun onRootViewsChanged(root: View, added: Boolean) {
    rootViewsLock.acquire().use {
      if (added) {
        rootViews.add(WeakReference(root))
        capturer?.recorder?.bind(root)
        determineWindowSize(root)
      } else {
        capturer?.recorder?.unbind(root)
        rootViews.removeAll { it.get() == root }

        val newRoot = rootViews.lastOrNull()?.get()
        if (newRoot != null && root != newRoot) {
          capturer?.recorder?.bind(newRoot)
          determineWindowSize(newRoot)
        } else {
          Unit // synchronized block wants us to return something lol
        }
      }
    }
  }

  fun determineWindowSize(root: View) {
    if (root.hasSize()) {
      if (root.width != lastKnownWindowSize.x && root.height != lastKnownWindowSize.y) {
        lastKnownWindowSize.set(root.width, root.height)
        windowCallback.onWindowSizeChanged(root.width, root.height)
      }
    } else {
      root.addOnPreDrawListenerSafe(
        object : ViewTreeObserver.OnPreDrawListener {
          override fun onPreDraw(): Boolean {
            val currentRoot = rootViews.lastOrNull()?.get()
            // in case the root changed in the meantime, ignore the preDraw of the outdate root
            if (root != currentRoot) {
              root.removeOnPreDrawListenerSafe(this)
              return true
            }
            if (root.hasSize()) {
              root.removeOnPreDrawListenerSafe(this)
              if (root.width != lastKnownWindowSize.x && root.height != lastKnownWindowSize.y) {
                lastKnownWindowSize.set(root.width, root.height)
                windowCallback.onWindowSizeChanged(root.width, root.height)
              }
            }
            return true
          }
        }
      )
    }
  }

  override fun start() {
    isRecording.getAndSet(true)
  }

  override fun onConfigurationChanged(config: ScreenshotRecorderConfig) {
    if (!isRecording.get()) {
      return
    }

    if (capturer == null) {
      capturerLock.acquire().use {
        if (capturer == null) {
          // don't recreate runnable for every config change, just update the config
          capturer = Capturer(options, mainLooperHandler)
        }
      }
    }

    capturer?.config = config
    capturer?.recorder = ScreenshotRecorder(config, options, this, screenshotRecorderCallback)

    val newRoot = rootViews.lastOrNull()?.get()
    if (newRoot != null) {
      capturer?.recorder?.bind(newRoot)
    }

    // Remove any existing callbacks to prevent concurrent capture loops
    mainLooperHandler.removeCallbacks(capturer)

    val posted =
      mainLooperHandler.postDelayed(
        capturer,
        100L, // delay the first run by a bit, to allow root view listener to register
      )
    if (!posted) {
      options.logger.log(
        WARNING,
        "Failed to post the capture runnable, main looper is shutting down.",
      )
    }
  }

  override fun resume() {
    capturer?.resume()
  }

  override fun pause() {
    capturer?.pause()
  }

  override fun reset() {
    lastKnownWindowSize.set(0, 0)
    rootViewsLock.acquire().use {
      rootViews.forEach { capturer?.recorder?.unbind(it.get()) }
      rootViews.clear()
    }
  }

  override fun stop() {
    capturer?.stop()
    capturerLock.acquire().use { capturer = null }
    isRecording.set(false)
  }

  override fun close() {
    reset()
    mainLooperHandler.removeCallbacks(capturer)
    backgroundProcessingHandlerLock.acquire().use {
      backgroundProcessingHandler?.removeCallbacksAndMessages(null)
      backgroundProcessingHandlerThread?.quitSafely()
    }
    stop()
  }

  override fun getExecutor(): ScheduledExecutorService = replayExecutor

  override fun getMainLooperHandler(): MainLooperHandler = mainLooperHandler

  override fun getBackgroundHandler(): Handler {
    // only start the background thread if it's actually needed, as it's only used by Canvas Capture
    // Strategy
    if (backgroundProcessingHandler == null) {
      backgroundProcessingHandlerLock.acquire().use {
        if (backgroundProcessingHandler == null) {
          backgroundProcessingHandlerThread = HandlerThread("SentryReplayBackgroundProcessing")
          backgroundProcessingHandlerThread?.start()
          backgroundProcessingHandler = Handler(backgroundProcessingHandlerThread!!.looper)
        }
      }
    }
    return backgroundProcessingHandler!!
  }
}

internal interface ExecutorProvider {
  /** Returns an executor suitable for background tasks. */
  fun getExecutor(): ScheduledExecutorService

  /** Returns a handler associated with the main thread looper. */
  fun getMainLooperHandler(): MainLooperHandler

  /** Returns a handler associated with a background thread looper. */
  fun getBackgroundHandler(): Handler
}
