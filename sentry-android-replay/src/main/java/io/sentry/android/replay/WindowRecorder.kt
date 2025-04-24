package io.sentry.android.replay

import android.annotation.TargetApi
import android.graphics.Point
import android.view.View
import android.view.ViewTreeObserver
import io.sentry.SentryOptions
import io.sentry.android.replay.util.MainLooperHandler
import io.sentry.android.replay.util.addOnDrawListenerSafe
import io.sentry.android.replay.util.gracefullyShutdown
import io.sentry.android.replay.util.hasSize
import io.sentry.android.replay.util.removeOnDrawListenerSafe
import io.sentry.android.replay.util.scheduleAtFixedRateSafely
import io.sentry.util.AutoClosableReentrantLock
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean

@TargetApi(26)
internal class WindowRecorder(
    private val options: SentryOptions,
    private val screenshotRecorderCallback: ScreenshotRecorderCallback? = null,
    private val windowCallback: WindowCallback,
    private val mainLooperHandler: MainLooperHandler,
    private val replayExecutor: ScheduledExecutorService
) : Recorder, OnRootViewsChangedListener, ConfigurationChangedListener {

    internal companion object {
        private const val TAG = "WindowRecorder"
    }

    private val isRecording = AtomicBoolean(false)
    private val rootViews = ArrayList<WeakReference<View>>()
    private var lastKnownWindowSize: Point = Point()
    private val rootViewsLock = AutoClosableReentrantLock()
    private var recorder: ScreenshotRecorder? = null
    private var capturingTask: ScheduledFuture<*>? = null
    private val capturer by lazy {
        Executors.newSingleThreadScheduledExecutor(RecorderExecutorServiceThreadFactory())
    }

    override fun onRootViewsChanged(root: View, added: Boolean) {
        rootViewsLock.acquire().use {
            if (added) {
                rootViews.add(WeakReference(root))
                recorder?.bind(root)
                determineWindowSize(root)
            } else {
                recorder?.unbind(root)
                rootViews.removeAll { it.get() == root }

                val newRoot = rootViews.lastOrNull()?.get()
                if (newRoot != null && root != newRoot) {
                    recorder?.bind(newRoot)
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
            root.addOnDrawListenerSafe(object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    val currentRoot = rootViews.lastOrNull()?.get()
                    if (root != currentRoot) {
                        return
                    }
                    if (root.hasSize()) {
                        if (root.width != lastKnownWindowSize.x && root.height != lastKnownWindowSize.y) {
                            lastKnownWindowSize.set(root.width, root.height)
                            windowCallback.onWindowSizeChanged(root.width, root.height)
                        }
                        root.removeOnDrawListenerSafe(this)
                    }
                }
            })
        }
    }

    override fun start(recorderConfig: ScreenshotRecorderConfig) {
        if (isRecording.getAndSet(true)) {
            return
        }

        recorder = ScreenshotRecorder(
            recorderConfig,
            options,
            mainLooperHandler,
            replayExecutor,
            screenshotRecorderCallback
        )

        val newRoot = rootViews.lastOrNull()?.get()
        if (newRoot != null) {
            recorder?.bind(newRoot)
        }
        // TODO: change this to use MainThreadHandler and just post on the main thread with delay
        // to avoid thread context switch every time
        capturingTask = capturer.scheduleAtFixedRateSafely(
            options,
            "$TAG.capture",
            100L, // delay the first run by a bit, to allow root view listener to register
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
        recorder?.close()
        recorder = null
        capturingTask?.cancel(false)
        capturingTask = null
        isRecording.set(false)
    }

    override fun close() {
        onConfigurationChanged()
        stop()
        capturer.gracefullyShutdown(options)
    }

    override fun onConfigurationChanged() {
        lastKnownWindowSize.set(0, 0)
        rootViewsLock.acquire().use {
            rootViews.forEach { recorder?.unbind(it.get()) }
            rootViews.clear()
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
