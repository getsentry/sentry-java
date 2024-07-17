package io.sentry.android.replay

import android.annotation.TargetApi
import android.view.MotionEvent
import android.view.View
import android.view.Window
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryOptions
import io.sentry.android.replay.util.FixedWindowCallback
import io.sentry.android.replay.util.MainLooperHandler
import io.sentry.android.replay.util.gracefullyShutdown
import io.sentry.android.replay.util.scheduleAtFixedRateSafely
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode.NONE

@TargetApi(26)
internal class WindowRecorder(
    private val options: SentryOptions,
    private val screenshotRecorderCallback: ScreenshotRecorderCallback? = null,
    private val touchRecorderCallback: TouchRecorderCallback? = null,
    private val mainLooperHandler: MainLooperHandler
) : Recorder {

    internal companion object {
        private const val TAG = "WindowRecorder"
    }

    private val rootViewsSpy by lazy(NONE) {
        RootViewsSpy.install()
    }

    private val isRecording = AtomicBoolean(false)
    private val rootViews = ArrayList<WeakReference<View>>()
    private var recorder: ScreenshotRecorder? = null
    private var capturingTask: ScheduledFuture<*>? = null
    private val capturer by lazy {
        Executors.newSingleThreadScheduledExecutor(RecorderExecutorServiceThreadFactory())
    }

    private val onRootViewsChangedListener = OnRootViewsChangedListener { root, added ->
        if (added) {
            rootViews.add(WeakReference(root))
            recorder?.bind(root)

            root.startGestureTracking()
        } else {
            root.stopGestureTracking()

            recorder?.unbind(root)
            rootViews.removeAll { it.get() == root }

            val newRoot = rootViews.lastOrNull()?.get()
            if (newRoot != null && root != newRoot) {
                recorder?.bind(newRoot)
            }
        }
    }

    override fun start(recorderConfig: ScreenshotRecorderConfig) {
        if (isRecording.getAndSet(true)) {
            return
        }

        recorder = ScreenshotRecorder(recorderConfig, options, mainLooperHandler, screenshotRecorderCallback)
        rootViewsSpy.listeners += onRootViewsChangedListener
        capturingTask = capturer.scheduleAtFixedRateSafely(
            options,
            "$TAG.capture",
            0L,
            1000L / recorderConfig.frameRate,
            MILLISECONDS
        ) {
            recorder?.capture()
        }
    }

    override fun resume() {
        recorder?.resume()
    }
    override fun pause() {
        recorder?.pause()
    }

    override fun stop() {
        rootViewsSpy.listeners -= onRootViewsChangedListener
        rootViews.forEach { recorder?.unbind(it.get()) }
        recorder?.close()
        rootViews.clear()
        recorder = null
        capturingTask?.cancel(false)
        capturingTask = null
        isRecording.set(false)
    }

    override fun close() {
        stop()
        capturer.gracefullyShutdown(options)
    }

    private fun View.startGestureTracking() {
        val window = phoneWindow
        if (window == null) {
            options.logger.log(DEBUG, "Window is invalid, not tracking gestures")
            return
        }

        if (touchRecorderCallback == null) {
            options.logger.log(DEBUG, "TouchRecorderCallback is null, not tracking gestures")
            return
        }

        val delegate = window.callback
        window.callback = SentryReplayGestureRecorder(options, touchRecorderCallback, delegate)
    }

    private fun View.stopGestureTracking() {
        val window = phoneWindow
        if (window == null) {
            options.logger.log(DEBUG, "Window was null in stopGestureTracking")
            return
        }

        if (window.callback is SentryReplayGestureRecorder) {
            val delegate = (window.callback as SentryReplayGestureRecorder).delegate
            window.callback = delegate
        }
    }

    private class SentryReplayGestureRecorder(
        private val options: SentryOptions,
        private val touchRecorderCallback: TouchRecorderCallback?,
        delegate: Window.Callback?
    ) : FixedWindowCallback(delegate) {
        override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
            if (event != null) {
                val copy: MotionEvent = MotionEvent.obtainNoHistory(event)
                try {
                    touchRecorderCallback?.onTouchEvent(copy)
                } catch (e: Throwable) {
                    options.logger.log(ERROR, "Error dispatching touch event", e)
                } finally {
                    copy.recycle()
                }
            }
            return super.dispatchTouchEvent(event)
        }
    }

    private class RecorderExecutorServiceThreadFactory : ThreadFactory {
        private var cnt = 0
        override fun newThread(r: Runnable): Thread {
            val ret = Thread(r, "SentryWindowRecorder-" + cnt++)
            ret.setDaemon(true)
            return ret
        }
    }
}

public interface TouchRecorderCallback {
    public fun onTouchEvent(event: MotionEvent)
}
