/**
 * Adapted from https://github.com/square/curtains/tree/v1.2.5
 *
 * Copyright 2021 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.sentry.android.replay

import android.annotation.SuppressLint
import android.os.Build.VERSION.SDK_INT
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import io.sentry.util.AutoClosableReentrantLock
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode.NONE

/**
 * If this view is part of the view hierarchy from a [android.app.Activity], [android.app.Dialog] or
 * [android.service.dreams.DreamService], then this returns the [android.view.Window] instance
 * associated to it. Otherwise, this returns null.
 *
 * Note: this property is called [phoneWindow] because the only implementation of [Window] is the
 * internal class android.view.PhoneWindow.
 */
internal val View.phoneWindow: Window?
  get() {
    return WindowSpy.pullWindow(rootView)
  }

@SuppressLint("PrivateApi")
internal object WindowSpy {
  /**
   * DecorView moved out of PhoneWindow into its own class:
   * https://android.googlesource.com/platform/frameworks/base/+/8804af2b63b0584034f7ec7d4dc701d06e6a8754
   * In API 24:
   * https://android.googlesource.com/platform/frameworks/base/+/android-7.0.0_r1/core/java/com/android/internal/policy/DecorView.java
   */
  private val decorViewClass by
    lazy(NONE) {
      try {
        Class.forName("com.android.internal.policy.DecorView")
      } catch (ignored: Throwable) {
        Log.d("WindowSpy", "Unexpected exception loading DecorView on API $SDK_INT", ignored)
        null
      }
    }

  /**
   * See [decorViewClass] for the AOSP history of the DecorView class. Between the latest API 23
   * release and the first API 24 release, DecorView first became a static class:
   * https://android.googlesource.com/platform/frameworks/base/+/0daf2102a20d224edeb4ee45dd4ee91889ef3e0c
   * Then it was extracted into a separate class.
   *
   * Hence we use "mWindow" on API 24+.
   */
  private val windowField by
    lazy(NONE) {
      decorViewClass?.let { decorViewClass ->
        try {
          decorViewClass.getDeclaredField("mWindow").apply { isAccessible = true }
        } catch (ignored: NoSuchFieldException) {
          Log.d(
            "WindowSpy",
            "Unexpected exception retrieving $decorViewClass#mWindow on API $SDK_INT",
            ignored,
          )
          null
        }
      }
    }

  fun pullWindow(maybeDecorView: View): Window? =
    decorViewClass?.let { decorViewClass ->
      if (decorViewClass.isInstance(maybeDecorView)) {
        windowField?.let { windowField -> windowField[maybeDecorView] as Window }
      } else {
        null
      }
    }
}

/**
 * Listener added to [Curtains.onRootViewsChangedListeners]. If you only care about either attached
 * or detached, consider implementing [OnRootViewAddedListener] or [OnRootViewRemovedListener]
 * instead.
 */
internal fun interface OnRootViewsChangedListener {
  /**
   * Called when [android.view.WindowManager.addView] and [android.view.WindowManager.removeView]
   * are called.
   */
  fun onRootViewsChanged(view: View, added: Boolean)
}

/** A utility that holds the list of root views that WindowManager updates. */
internal class RootViewsSpy private constructor() : Closeable {
  private val isClosed = AtomicBoolean(false)
  private val viewListLock = AutoClosableReentrantLock()

  val listeners: CopyOnWriteArrayList<OnRootViewsChangedListener> =
    object : CopyOnWriteArrayList<OnRootViewsChangedListener>() {
      override fun add(element: OnRootViewsChangedListener?): Boolean {
        viewListLock.acquire().use {
          // notify listener about existing root views immediately
          delegatingViewList.forEach { element?.onRootViewsChanged(it, true) }
        }
        return super.add(element)
      }
    }

  private val delegatingViewList: ArrayList<View> =
    object : ArrayList<View>() {
      @Suppress("NewApi")
      override fun addAll(elements: Collection<View>): Boolean {
        listeners.forEach { listener ->
          elements.forEach { element -> listener.onRootViewsChanged(element, true) }
        }
        return super.addAll(elements)
      }

      @Suppress("NewApi")
      override fun add(element: View): Boolean {
        listeners.forEach { it.onRootViewsChanged(element, true) }
        return super.add(element)
      }

      @Suppress("NewApi")
      override fun removeAt(index: Int): View {
        val removedView = super.removeAt(index)
        listeners.forEach { it.onRootViewsChanged(removedView, false) }
        return removedView
      }
    }

  override fun close() {
    isClosed.set(true)
    listeners.clear()
  }

  companion object {
    fun install(): RootViewsSpy {
      return RootViewsSpy().apply {
        // had to do this on the main thread queue, otherwise if this is
        // called from ContentProvider, it might be too early and the listener won't be installed
        Handler(Looper.getMainLooper()).postAtFrontOfQueue {
          if (isClosed.get()) {
            return@postAtFrontOfQueue
          }
          WindowManagerSpy.swapWindowManagerGlobalMViews { mViews ->
            viewListLock.acquire().use { delegatingViewList.apply { addAll(mViews) } }
          }
        }
      }
    }
  }
}

internal object WindowManagerSpy {
  private val windowManagerClass by
    lazy(NONE) {
      val className = "android.view.WindowManagerGlobal"
      try {
        Class.forName(className)
      } catch (ignored: Throwable) {
        Log.w("WindowManagerSpy", ignored)
        null
      }
    }

  private val windowManagerInstance by
    lazy(NONE) { windowManagerClass?.getMethod("getInstance")?.invoke(null) }

  private val mViewsField by
    lazy(NONE) {
      windowManagerClass?.let { windowManagerClass ->
        windowManagerClass.getDeclaredField("mViews").apply { isAccessible = true }
      }
    }

  // You can discourage me all you want I'll still do it.
  @SuppressLint("PrivateApi", "ObsoleteSdkInt", "DiscouragedPrivateApi")
  fun swapWindowManagerGlobalMViews(swap: (ArrayList<View>) -> ArrayList<View>) {
    try {
      windowManagerInstance?.let { windowManagerInstance ->
        mViewsField?.let { mViewsField ->
          @Suppress("UNCHECKED_CAST")
          val mViews = mViewsField[windowManagerInstance] as ArrayList<View>
          mViewsField[windowManagerInstance] = swap(mViews)
        }
      }
    } catch (ignored: Throwable) {
      Log.w("WindowManagerSpy", ignored)
    }
  }
}
