package io.sentry.android.replay

import android.annotation.TargetApi
import android.view.View
import io.sentry.SentryOptions
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
    private val screenshotRecorderCallback: ScreenshotRecorderCallback? = null
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
        } else {
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

        recorder = ScreenshotRecorder(recorderConfig, options, screenshotRecorderCallback)
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

    private class RecorderExecutorServiceThreadFactory : ThreadFactory {
        private var cnt = 0
        override fun newThread(r: Runnable): Thread {
            val ret = Thread(r, "SentryWindowRecorder-" + cnt++)
            ret.setDaemon(true)
            return ret
        }
    }

    override fun close() {
        stop()
        capturer.gracefullyShutdown(options)
    }
}
