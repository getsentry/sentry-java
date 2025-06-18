package io.sentry.android.core.internal.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.BuildInfoProvider
import io.sentry.test.getProperty
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class FirstDrawDoneListenerTest {
    private class Fixture {
        val application: Context = ApplicationProvider.getApplicationContext()
        val buildInfo = mock<BuildInfoProvider>()
        lateinit var onDrawListeners: ArrayList<ViewTreeObserver.OnDrawListener>

        fun getSut(apiVersion: Int = Build.VERSION_CODES.O): View {
            whenever(buildInfo.sdkInfoVersion).thenReturn(apiVersion)
            val view = View(application)

            // Adding a listener forces ViewTreeObserver.mOnDrawListeners to be initialized and non-null.
            val dummyListener = ViewTreeObserver.OnDrawListener {}
            view.viewTreeObserver.addOnDrawListener(dummyListener)
            view.viewTreeObserver.removeOnDrawListener(dummyListener)

            // Obtain mOnDrawListeners field through reflection
            onDrawListeners = view.viewTreeObserver.getProperty("mOnDrawListeners")
            assertTrue(onDrawListeners.isEmpty())

            return view
        }
    }

    private val fixture = Fixture()

    @Test
    fun `registerForNextDraw adds listener on attach state changed on sdk 25-`() {
        val view = fixture.getSut(Build.VERSION_CODES.N_MR1)

        // OnDrawListener is not registered, it is delayed for later
        FirstDrawDoneListener.registerForNextDraw(view, {}, fixture.buildInfo)
        assertTrue(fixture.onDrawListeners.isEmpty())

        // Register listener after the view is attached to a window
        val listenerInfo = Class.forName("android.view.View\$ListenerInfo")
        val mListenerInfo: Any = view.getProperty("mListenerInfo")
        val mOnAttachStateChangeListeners: CopyOnWriteArrayList<View.OnAttachStateChangeListener> =
            mListenerInfo.getProperty(listenerInfo, "mOnAttachStateChangeListeners")
        assertFalse(mOnAttachStateChangeListeners.isEmpty())

        // Dispatch onViewAttachedToWindow()
        for (listener in mOnAttachStateChangeListeners) {
            listener.onViewAttachedToWindow(view)
        }

        assertFalse(fixture.onDrawListeners.isEmpty())
        assertIs<FirstDrawDoneListener>(fixture.onDrawListeners[0])

        // mOnAttachStateChangeListeners is automatically removed
        assertTrue(mOnAttachStateChangeListeners.isEmpty())
    }

    @Test
    fun `registerForNextDraw adds listener on sdk 26+`() {
        val view = fixture.getSut()

        // Immediately register an OnDrawListener to ViewTreeObserver
        FirstDrawDoneListener.registerForNextDraw(view, {}, fixture.buildInfo)
        assertFalse(fixture.onDrawListeners.isEmpty())
        assertIs<FirstDrawDoneListener>(fixture.onDrawListeners[0])
    }

    @Test
    fun `registerForNextDraw posts callback to front of queue`() {
        val view = fixture.getSut()
        val handler = Handler(Looper.getMainLooper())
        val drawDoneCallback = mock<Runnable>()
        val otherCallback = mock<Runnable>()
        val inOrder = inOrder(drawDoneCallback, otherCallback)
        FirstDrawDoneListener.registerForNextDraw(view, drawDoneCallback, fixture.buildInfo)
        handler.post(otherCallback) // 3rd in queue
        handler.postAtFrontOfQueue(otherCallback) // 2nd in queue
        view.viewTreeObserver.dispatchOnDraw() // 1st in queue
        verify(drawDoneCallback, never()).run()
        verify(otherCallback, never()).run()

        // Execute all posted tasks
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        inOrder.verify(drawDoneCallback).run()
        inOrder.verify(otherCallback, times(2)).run()
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `registerForNextDraw unregister itself after onDraw`() {
        val view = fixture.getSut()
        FirstDrawDoneListener.registerForNextDraw(view, {}, fixture.buildInfo)
        assertFalse(fixture.onDrawListeners.isEmpty())

        // Does not remove OnDrawListener before onDraw, even if OnGlobalLayout is triggered
        view.viewTreeObserver.dispatchOnGlobalLayout()
        assertFalse(fixture.onDrawListeners.isEmpty())

        // Removes OnDrawListener in the next OnGlobalLayout after onDraw
        view.viewTreeObserver.dispatchOnDraw()
        view.viewTreeObserver.dispatchOnGlobalLayout()
        assertTrue(fixture.onDrawListeners.isEmpty())
    }

    @Test
    fun `registerForNextDraw calls the given callback on the main thread after onDraw`() {
        val view = fixture.getSut()
        val r: Runnable = mock()
        FirstDrawDoneListener.registerForNextDraw(view, r, fixture.buildInfo)
        view.viewTreeObserver.dispatchOnDraw()

        // Execute all tasks posted to main looper
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        verify(r).run()
    }

    @Test
    fun `registerForNextDraw uses the activity decor view`() {
        val view = fixture.getSut()

        val activity = mock<Activity>()
        val window = mock<Window>()
        whenever(activity.window).thenReturn(window)
        whenever(window.peekDecorView()).thenReturn(view)

        val r: Runnable = mock()
        FirstDrawDoneListener.registerForNextDraw(activity, r, fixture.buildInfo)

        assertFalse(fixture.onDrawListeners.isEmpty())
    }

    @Test
    fun `registerForNextDraw uses the activity decor view once it's available`() {
        val view = fixture.getSut()

        val activity = mock<Activity>()
        val window = mock<Window>()
        whenever(activity.window).thenReturn(window)
        whenever(window.peekDecorView()).thenReturn(null)
        val callbackCapture = argumentCaptor<Window.Callback>()

        // when registerForNextDraw is called, but the activity has no window yet
        val r: Runnable = mock()
        FirstDrawDoneListener.registerForNextDraw(activity, r, fixture.buildInfo)

        // then a window callback is installed
        verify(window).callback = callbackCapture.capture()

        // once the window is available
        whenever(window.peekDecorView()).thenReturn(view)
        callbackCapture.firstValue.onContentChanged()

        // then a window callback should be set back to the original one
        verify(window, times(2)).callback = callbackCapture.capture()
        assertNull(callbackCapture.lastValue)

        // and the onDrawListener should be registered
        assertFalse(fixture.onDrawListeners.isEmpty())

        listOf(1, 2).isNotEmpty()
    }
}
