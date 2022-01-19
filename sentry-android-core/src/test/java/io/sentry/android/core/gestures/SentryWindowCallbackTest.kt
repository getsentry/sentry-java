package io.sentry.android.core.gestures

import android.view.MotionEvent
import android.view.Window
import androidx.core.view.GestureDetectorCompat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.android.core.gestures.SentryWindowCallback.MotionEventObtainer
import org.junit.Test

class SentryWindowCallbackTest {
    class Fixture {
        val delegate: Window.Callback = mock()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val gestureDetector: GestureDetectorCompat = mock()
        val gestureListener: SentryGestureListener = mock()
        val motionEventCopy: MotionEvent = mock()

        fun getSut(): SentryWindowCallback {
            return SentryWindowCallback(
                delegate,
                gestureDetector,
                gestureListener,
                options,
                object : MotionEventObtainer {
                    override fun obtain(origin: MotionEvent): MotionEvent {
                        val actionMasked = origin.actionMasked
                        whenever(motionEventCopy.actionMasked).doReturn(actionMasked)
                        return motionEventCopy
                    }
                })
        }
    }

    private val fixture = Fixture()

    @Test
    fun `delegates the events to the gesture detector`() {
        val event: MotionEvent = mock()
        val sut = fixture.getSut()

        sut.dispatchTouchEvent(event)

        verify(fixture.gestureDetector).onTouchEvent(fixture.motionEventCopy)
        verify(fixture.motionEventCopy).recycle()
    }

    @Test
    fun `on action up will call the gesture listener after delegating to gesture detector`() {
        val event: MotionEvent = mock {
            whenever(it.actionMasked).thenReturn(MotionEvent.ACTION_UP)
        }
        val sut = fixture.getSut()

        sut.dispatchTouchEvent(event)

        inOrder(fixture.gestureDetector, fixture.gestureListener) {
            verify(fixture.gestureDetector).onTouchEvent(fixture.motionEventCopy)
            verify(fixture.gestureListener).onUp(fixture.motionEventCopy)
        }
    }

    @Test
    fun `other events are ignored for gesture listener`() {
        val event: MotionEvent = mock {
            whenever(it.actionMasked).thenReturn(MotionEvent.ACTION_DOWN)
        }
        val sut = fixture.getSut()

        sut.dispatchTouchEvent(event)

        verify(fixture.gestureListener, never()).onUp(any())
    }

    @Test
    fun `nullable event is ignored`() {
        val sut = fixture.getSut()

        sut.dispatchTouchEvent(null)

        verify(fixture.gestureDetector, never()).onTouchEvent(any())
    }
}
