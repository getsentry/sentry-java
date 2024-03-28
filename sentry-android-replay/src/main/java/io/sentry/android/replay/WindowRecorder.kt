package io.sentry.android.replay

import android.annotation.TargetApi
import android.view.View
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryOptions
import java.io.Closeable
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
    private val recorderConfig: ScreenshotRecorderConfig,
    private val screenshotRecorderCallback: ScreenshotRecorderCallback
) : Closeable {

    private val rootViewsSpy by lazy(NONE) {
        RootViewsSpy.install()
    }

    private val isRecording = AtomicBoolean(false)
    private val rootViews = ArrayList<WeakReference<View>>()
    private var recorder: ScreenshotRecorder? = null
    private var capturingTask: ScheduledFuture<*>? = null
    private val capturer = Executors.newSingleThreadScheduledExecutor(RecorderExecutorServiceThreadFactory())

    private val onRootViewsChangedListener = OnRootViewsChangedListener { root, added ->
        if (added) {
            rootViews.add(WeakReference(root))
            recorder?.bind(root)
        } else {
            recorder?.unbind(root)
            rootViews.removeAll { it.get() == root }

            val newRoot = rootViews.lastOrNull()?.get()
            if (newRoot != null && root != newRoot) {
                recorder?.bind(newRoot)
            }
        }
    }

    fun startRecording() {
        if (isRecording.getAndSet(true)) {
            return
        }

//    val (height, width) = (wm.currentWindowMetrics.bounds.bottom /
//        context.resources.displayMetrics.density).roundToInt() to
//        (wm.currentWindowMetrics.bounds.right /
//            context.resources.displayMetrics.density).roundToInt()

        recorder = ScreenshotRecorder(recorderConfig, options, screenshotRecorderCallback)
        rootViewsSpy.listeners += onRootViewsChangedListener
        capturingTask = capturer.scheduleAtFixedRate({
            try {
                recorder?.capture()
            } catch (e: Throwable) {
                options.logger.log(ERROR, "Failed to capture a screenshot with exception:", e)
                // TODO: I guess schedule the capturer again, cause it will stop executing the runnable?
            }
        }, 0L, 1000L / recorderConfig.frameRate, MILLISECONDS)
    }

    fun resume() = recorder?.resume()
    fun pause() = recorder?.pause()

    fun stopRecording() {
        rootViewsSpy.listeners -= onRootViewsChangedListener
        rootViews.forEach { recorder?.unbind(it.get()) }
        recorder?.close()
        rootViews.clear()
        recorder = null
        capturingTask?.cancel(false)
        capturingTask = null
        isRecording.set(false)
    }

    private class RecorderExecutorServiceThreadFactory : ThreadFactory {
        private var cnt = 0
        override fun newThread(r: Runnable): Thread {
            val ret = Thread(r, "SentryWindowRecorder-" + cnt++)
            ret.setDaemon(true)
            return ret
        }
    }

    override fun close() {
        stopRecording()
        capturer.gracefullyShutdown(options)
    }
}
