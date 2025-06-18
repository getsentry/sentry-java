package io.sentry.android.core.internal.gestures

import android.view.MotionEvent
import android.view.Window
import androidx.core.view.GestureDetectorCompat
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.android.core.internal.gestures.SentryWindowCallback.MotionEventObtainer
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test

class SentryWindowCallbackTest {
    class Fixture {
        val delegate = mock<Window.Callback>()
        val options =
            SentryAndroidOptions().apply {
                dsn = "https://key@sentry.io/proj"
            }
        val gestureDetector = mock<GestureDetectorCompat>()
        val gestureListener = mock<SentryGestureListener>()
        val motionEventCopy = mock<MotionEvent>()

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
                },
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `delegates the events to the gesture detector`() {
        val event = mock<MotionEvent>()
        val sut = fixture.getSut()

        sut.dispatchTouchEvent(event)

        verify(fixture.gestureDetector).onTouchEvent(fixture.motionEventCopy)
        verify(fixture.motionEventCopy).recycle()
    }

    @Test
    fun `on action up will call the gesture listener after delegating to gesture detector`() {
        val event =
            mock<MotionEvent> {
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
        val event =
            mock<MotionEvent> {
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
