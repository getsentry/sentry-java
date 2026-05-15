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
import java.util.WeakHashMap

internal class GestureRecorder(
  private val options: SentryOptions,
  private val touchRecorderCallback: TouchRecorderCallback,
) : OnRootViewsChangedListener {
  private val rootViews = ArrayList<WeakReference<View>>()
  private val rootViewsLock = AutoClosableReentrantLock()

  // WeakReference value, because the callback chain strongly references the wrapper — a strong
  // value would prevent the window from ever being GC'd.
  private val wrappedWindows = WeakHashMap<Window, WeakReference<SentryReplayGestureRecorder>>()
  private val wrappedWindowsLock = AutoClosableReentrantLock()

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

    wrappedWindowsLock.acquire().use {
      if (wrappedWindows[window]?.get() != null) {
        return
      }
    }

    val delegate = window.callback
    val wrapper = SentryReplayGestureRecorder(options, touchRecorderCallback, delegate)
    window.callback = wrapper
    wrappedWindowsLock.acquire().use { wrappedWindows[window] = WeakReference(wrapper) }
  }

  private fun View.stopGestureTracking() {
    val window = phoneWindow
    if (window == null) {
      options.logger.log(DEBUG, "Window was null in stopGestureTracking")
      return
    }

    val callback = window.callback
    if (callback is SentryReplayGestureRecorder) {
      window.callback = callback.delegate
      wrappedWindowsLock.acquire().use { wrappedWindows.remove(window) }
      return
    }

    // Another wrapper (e.g. UserInteractionIntegration) sits on top of ours — cutting it out of
    // the chain would break its instrumentation, so we inert our buried wrapper instead. The
    // next replay session will then wrap on top with a fresh active instance.
    val ours: SentryReplayGestureRecorder?
    wrappedWindowsLock.acquire().use {
      ours = wrappedWindows[window]?.get()
      wrappedWindows.remove(window)
    }
    ours?.inert()
  }

  internal class SentryReplayGestureRecorder(
    private val options: SentryOptions,
    @Volatile private var touchRecorderCallback: TouchRecorderCallback?,
    delegate: Window.Callback?,
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

    /**
     * Turns this wrapper into a passthrough when it can't be removed from the chain (another
     * wrapper sits on top). Subsequent dispatches only delegate, skipping the recorder callback.
     */
    fun inert() {
      touchRecorderCallback = null
    }
  }
}

public interface TouchRecorderCallback {
  public fun onTouchEvent(event: MotionEvent)
}
