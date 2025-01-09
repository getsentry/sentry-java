package io.sentry.android.replay.gestures

import android.view.MotionEvent
import android.view.View
import android.view.Window
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryOptions
import io.sentry.android.replay.OnRootViewsChangedListener
import io.sentry.android.replay.phoneWindow
import io.sentry.android.replay.util.FixedWindowCallback
import io.sentry.util.AutoClosableReentrantLock
import java.lang.ref.WeakReference

class GestureRecorder(
    private val options: SentryOptions,
    private val touchRecorderCallback: TouchRecorderCallback
) : OnRootViewsChangedListener {

    private val rootViews = ArrayList<WeakReference<View>>()
    private val rootViewsLock = AutoClosableReentrantLock()

    override fun onRootViewsChanged(root: View, added: Boolean) {
        rootViewsLock.acquire().use {
            if (added) {
                rootViews.add(WeakReference(root))
                root.startGestureTracking()
            } else {
                root.stopGestureTracking()
                rootViews.removeAll { it.get() == root }
            }
        }
    }

    fun stop() {
        rootViewsLock.acquire().use {
            rootViews.forEach { it.get()?.stopGestureTracking() }
            rootViews.clear()
        }
    }

    private fun View.startGestureTracking() {
        val window = phoneWindow
        if (window == null) {
            options.logger.log(DEBUG, "Window is invalid, not tracking gestures")
            return
        }

        val delegate = window.callback
        if (delegate !is SentryReplayGestureRecorder) {
            window.callback = SentryReplayGestureRecorder(options, touchRecorderCallback, delegate)
        }
    }

    private fun View.stopGestureTracking() {
        val window = phoneWindow
        if (window == null) {
            options.logger.log(DEBUG, "Window was null in stopGestureTracking")
            return
        }

        val callback = window.callback
        if (callback is SentryReplayGestureRecorder) {
            val delegate = callback.delegate
            window.callback = delegate
        }
    }

    internal class SentryReplayGestureRecorder(
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
}

public interface TouchRecorderCallback {
    fun onTouchEvent(event: MotionEvent)
}
