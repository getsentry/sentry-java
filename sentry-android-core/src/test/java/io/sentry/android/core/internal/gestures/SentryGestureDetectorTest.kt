package io.sentry.android.core.internal.gestures

import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class SentryGestureDetectorTest {

  class Fixture {
    val listener = mock<GestureDetector.OnGestureListener>()
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    fun getSut(): SentryGestureDetector {
      return SentryGestureDetector(context, listener)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `tap - DOWN followed by UP within touch slop fires onSingleTapUp`() {
    val sut = fixture.getSut()
    val downTime = SystemClock.uptimeMillis()

    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
    val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, 100f, 100f, 0)

    sut.onTouchEvent(down)
    sut.onTouchEvent(up)

    verify(fixture.listener).onDown(down)
    verify(fixture.listener).onSingleTapUp(up)
    verify(fixture.listener, never()).onScroll(any(), any(), any(), any())
    verify(fixture.listener, never()).onFling(anyOrNull(), any(), any(), any())

    down.recycle()
    up.recycle()
  }

  @Test
  fun `no tap - DOWN followed by MOVE beyond slop and UP does not fire onSingleTapUp`() {
    val sut = fixture.getSut()
    val downTime = SystemClock.uptimeMillis()
    val beyondSlop = fixture.touchSlop + 10f

    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
    val move =
      MotionEvent.obtain(
        downTime,
        downTime + 16,
        MotionEvent.ACTION_MOVE,
        100f + beyondSlop,
        100f,
        0,
      )
    val up =
      MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, 100f + beyondSlop, 100f, 0)

    sut.onTouchEvent(down)
    sut.onTouchEvent(move)
    sut.onTouchEvent(up)

    verify(fixture.listener, never()).onSingleTapUp(any())

    down.recycle()
    move.recycle()
    up.recycle()
  }

  @Test
  fun `scroll - DOWN followed by MOVE beyond slop fires onScroll with correct deltas`() {
    val sut = fixture.getSut()
    val downTime = SystemClock.uptimeMillis()
    val beyondSlop = fixture.touchSlop + 10f

    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
    val move =
      MotionEvent.obtain(
        downTime,
        downTime + 16,
        MotionEvent.ACTION_MOVE,
        100f + beyondSlop,
        200f,
        0,
      )

    sut.onTouchEvent(down)
    sut.onTouchEvent(move)

    // scrollX = lastX - currentX = 100 - (100 + beyondSlop) = -beyondSlop
    verify(fixture.listener).onScroll(anyOrNull(), eq(move), eq(-beyondSlop), eq(0f))

    down.recycle()
    move.recycle()
  }

  @Test
  fun `fling - fast swipe fires onFling`() {
    val sut = fixture.getSut()
    val downTime = SystemClock.uptimeMillis()
    val beyondSlop = fixture.touchSlop + 10f

    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
    // Move far and fast (large distance in short time = high velocity)
    val move =
      MotionEvent.obtain(
        downTime,
        downTime + 10,
        MotionEvent.ACTION_MOVE,
        100f + beyondSlop,
        100f,
        0,
      )
    val up = MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_UP, 500f, 100f, 0)

    sut.onTouchEvent(down)
    sut.onTouchEvent(move)
    sut.onTouchEvent(up)

    verify(fixture.listener).onFling(anyOrNull(), eq(up), any(), any())

    down.recycle()
    move.recycle()
    up.recycle()
  }

  @Test
  fun `slow release - DOWN MOVE and slow UP does not fire onFling`() {
    val sut = fixture.getSut()
    val downTime = SystemClock.uptimeMillis()
    val beyondSlop = fixture.touchSlop + 1f

    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
    // Move just beyond slop
    val move =
      MotionEvent.obtain(
        downTime,
        downTime + 100,
        MotionEvent.ACTION_MOVE,
        100f + beyondSlop,
        100f,
        0,
      )
    // Stay at the same position for a long time to ensure near-zero velocity
    val moveStill =
      MotionEvent.obtain(
        downTime,
        downTime + 10000,
        MotionEvent.ACTION_MOVE,
        100f + beyondSlop,
        100f,
        0,
      )
    val up =
      MotionEvent.obtain(
        downTime,
        downTime + 10001,
        MotionEvent.ACTION_UP,
        100f + beyondSlop,
        100f,
        0,
      )

    sut.onTouchEvent(down)
    sut.onTouchEvent(move)
    sut.onTouchEvent(moveStill)
    sut.onTouchEvent(up)

    verify(fixture.listener, never()).onFling(anyOrNull(), any(), any(), any())

    down.recycle()
    move.recycle()
    moveStill.recycle()
    up.recycle()
  }

  @Test
  fun `cancel - DOWN followed by CANCEL does not fire tap or fling callbacks`() {
    val sut = fixture.getSut()
    val downTime = SystemClock.uptimeMillis()

    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
    val cancel =
      MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_CANCEL, 100f, 100f, 0)

    sut.onTouchEvent(down)
    sut.onTouchEvent(cancel)

    verify(fixture.listener).onDown(down)
    verify(fixture.listener, never()).onSingleTapUp(any())
    verify(fixture.listener, never()).onScroll(any(), any(), any(), any())
    verify(fixture.listener, never()).onFling(anyOrNull(), any(), any(), any())

    down.recycle()
    cancel.recycle()
  }

  @Test
  fun `sequential gestures - state resets between tap and scroll`() {
    val sut = fixture.getSut()
    val beyondSlop = fixture.touchSlop + 10f

    // First gesture: tap
    var downTime = SystemClock.uptimeMillis()
    val down1 = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
    val up1 = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, 100f, 100f, 0)

    sut.onTouchEvent(down1)
    sut.onTouchEvent(up1)
    verify(fixture.listener).onSingleTapUp(up1)

    // Second gesture: scroll
    downTime = SystemClock.uptimeMillis()
    val down2 = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 200f, 0)
    val move2 =
      MotionEvent.obtain(
        downTime,
        downTime + 16,
        MotionEvent.ACTION_MOVE,
        200f + beyondSlop,
        200f,
        0,
      )
    val up2 =
      MotionEvent.obtain(
        downTime,
        downTime + 5000,
        MotionEvent.ACTION_UP,
        200f + beyondSlop,
        200f,
        0,
      )

    sut.onTouchEvent(down2)
    sut.onTouchEvent(move2)
    sut.onTouchEvent(up2)

    verify(fixture.listener).onScroll(anyOrNull(), eq(move2), any(), any())
    // onSingleTapUp should NOT have been called again for the second gesture
    verify(fixture.listener, never()).onSingleTapUp(up2)

    // Third gesture: another tap to verify clean reset
    downTime = SystemClock.uptimeMillis()
    val down3 = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 300f, 300f, 0)
    val up3 = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, 300f, 300f, 0)

    sut.onTouchEvent(down3)
    sut.onTouchEvent(up3)
    verify(fixture.listener).onSingleTapUp(up3)

    down1.recycle()
    up1.recycle()
    down2.recycle()
    move2.recycle()
    up2.recycle()
    down3.recycle()
    up3.recycle()
  }
}
