package io.sentry.android.replay.gestures

import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.rrweb.RRWebInteractionEvent
import io.sentry.rrweb.RRWebInteractionMoveEvent
import io.sentry.transport.ICurrentDateProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class ReplayGestureConverterTest {
  internal class Fixture {
    var now: Long = 1000L

    fun getSut(
      dateProvider: ICurrentDateProvider = ICurrentDateProvider { now }
    ): ReplayGestureConverter = ReplayGestureConverter(dateProvider)
  }

  private val fixture = Fixture()

  @Test
  fun `convert ACTION_DOWN event`() {
    val sut = fixture.getSut()
    val recorderConfig = ScreenshotRecorderConfig(scaleFactorX = 1f, scaleFactorY = 1f)
    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)

    val result = sut.convert(event, recorderConfig)

    assertNotNull(result)
    assertEquals(1, result.size)
    assertTrue(result[0] is RRWebInteractionEvent)
    with(result[0] as RRWebInteractionEvent) {
      assertEquals(1000L, timestamp)
      assertEquals(100f, x)
      assertEquals(200f, y)
      assertEquals(0, id)
      assertEquals(0, pointerId)
      assertEquals(RRWebInteractionEvent.InteractionType.TouchStart, interactionType)
    }

    event.recycle()
  }

  @Test
  fun `convert ACTION_MOVE event with debounce`() {
    val sut = fixture.getSut()
    val recorderConfig = ScreenshotRecorderConfig(scaleFactorX = 1f, scaleFactorY = 1f)
    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 100f, 200f, 0)

    // First call should pass
    var result = sut.convert(event, recorderConfig)
    assertNotNull(result)

    // Second call within debounce threshold should be null
    fixture.now += 40 // Increase time by 40ms
    result = sut.convert(event, recorderConfig)
    assertNull(result)

    event.recycle()
  }

  @Test
  fun `convert ACTION_MOVE event with capture threshold`() {
    val sut = fixture.getSut()
    val recorderConfig = ScreenshotRecorderConfig(scaleFactorX = 1f, scaleFactorY = 1f)
    val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
    val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 110f, 210f, 0)

    // Add a pointer to currentPositions
    sut.convert(downEvent, recorderConfig)

    // First call should not trigger capture
    var result = sut.convert(moveEvent, recorderConfig)
    assertNull(result)

    // Second call should trigger capture
    fixture.now += 600 // Increase time by 600ms
    result = sut.convert(moveEvent, recorderConfig)
    assertNotNull(result)
    with(result[0] as RRWebInteractionMoveEvent) {
      assertEquals(1600L, timestamp)
      assertEquals(2, positions!!.size)
      assertEquals(110f, positions!![0].x)
      assertEquals(210f, positions!![0].y)
      assertEquals(0, positions!![0].id)
      assertEquals(0, pointerId)
    }

    downEvent.recycle()
    moveEvent.recycle()
  }

  @Test
  fun `convert ACTION_UP event`() {
    val sut = fixture.getSut()
    val recorderConfig = ScreenshotRecorderConfig(scaleFactorX = 1f, scaleFactorY = 1f)
    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 100f, 200f, 0)

    val result = sut.convert(event, recorderConfig)

    assertNotNull(result)
    assertEquals(1, result.size)
    assertTrue(result[0] is RRWebInteractionEvent)
    with(result[0] as RRWebInteractionEvent) {
      assertEquals(1000L, timestamp)
      assertEquals(100f, x)
      assertEquals(200f, y)
      assertEquals(0, id)
      assertEquals(0, pointerId)
      assertEquals(RRWebInteractionEvent.InteractionType.TouchEnd, interactionType)
    }

    event.recycle()
  }

  @Test
  fun `convert ACTION_CANCEL event`() {
    val sut = fixture.getSut()
    val recorderConfig = ScreenshotRecorderConfig(scaleFactorX = 1f, scaleFactorY = 1f)
    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 100f, 200f, 0)

    val result = sut.convert(event, recorderConfig)

    assertNotNull(result)
    assertEquals(1, result.size)
    assertTrue(result[0] is RRWebInteractionEvent)
    with(result[0] as RRWebInteractionEvent) {
      assertEquals(1000L, timestamp)
      assertEquals(100f, x)
      assertEquals(200f, y)
      assertEquals(0, id)
      assertEquals(0, pointerId)
      assertEquals(RRWebInteractionEvent.InteractionType.TouchCancel, interactionType)
    }

    event.recycle()
  }

  @Test
  fun `convert event with different scale factors`() {
    val sut = fixture.getSut()
    val customRecorderConfig = ScreenshotRecorderConfig(scaleFactorX = 0.5f, scaleFactorY = 1.5f)
    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)

    val result = sut.convert(event, customRecorderConfig)

    assertNotNull(result)
    assertEquals(1, result.size)
    assertTrue(result[0] is RRWebInteractionEvent)
    with(result[0] as RRWebInteractionEvent) {
      assertEquals(1000L, timestamp)
      assertEquals(50f, x) // 100 * 0.5
      assertEquals(300f, y) // 200 * 1.5
      assertEquals(0, id)
      assertEquals(0, pointerId)
      assertEquals(RRWebInteractionEvent.InteractionType.TouchStart, interactionType)
    }

    event.recycle()
  }

  @Test
  fun `convert multi-pointer events`() {
    val sut = fixture.getSut()
    val recorderConfig = ScreenshotRecorderConfig(scaleFactorX = 1f, scaleFactorY = 1f)

    // Simulate first finger down
    var event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
    var result = sut.convert(event, recorderConfig)
    assertNotNull(result)
    assertTrue(result[0] is RRWebInteractionEvent)
    assertEquals(
      RRWebInteractionEvent.InteractionType.TouchStart,
      (result[0] as RRWebInteractionEvent).interactionType,
    )
    event.recycle()

    // Simulate second finger down
    val properties = MotionEvent.PointerProperties()
    properties.id = 1
    properties.toolType = MotionEvent.TOOL_TYPE_FINGER
    val pointerProperties = arrayOf(MotionEvent.PointerProperties(), properties)
    val pointerCoords =
      arrayOf(
        MotionEvent.PointerCoords().apply {
          x = 100f
          y = 100f
        },
        MotionEvent.PointerCoords().apply {
          x = 200f
          y = 200f
        },
      )
    event =
      MotionEvent.obtain(
        0,
        1,
        MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        2,
        pointerProperties,
        pointerCoords,
        0,
        0,
        1f,
        1f,
        0,
        0,
        0,
        0,
      )
    fixture.now += 100 // Increase time by 100ms
    result = sut.convert(event, recorderConfig)
    assertNotNull(result)
    assertTrue(result[0] is RRWebInteractionEvent)
    assertEquals(
      RRWebInteractionEvent.InteractionType.TouchStart,
      (result[0] as RRWebInteractionEvent).interactionType,
    )
    assertEquals(1, (result[0] as RRWebInteractionEvent).pointerId)
    event.recycle()

    // Simulate move event
    pointerCoords[0].x = 90f
    pointerCoords[0].y = 90f
    pointerCoords[1].x = 210f
    pointerCoords[1].y = 210f
    event =
      MotionEvent.obtain(
        0,
        2,
        MotionEvent.ACTION_MOVE,
        2,
        pointerProperties,
        pointerCoords,
        0,
        0,
        1f,
        1f,
        0,
        0,
        0,
        0,
      )
    // First call should not trigger capture
    result = sut.convert(event, recorderConfig)
    assertNull(result)

    fixture.now += 600 // Increase time by 600ms to trigger move capture
    result = sut.convert(event, recorderConfig)
    assertNotNull(result)
    assertTrue((result[0] as RRWebInteractionMoveEvent).positions!!.size == 2)
    event.recycle()

    // Simulate second finger up
    event =
      MotionEvent.obtain(
        0,
        3,
        MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        2,
        pointerProperties,
        pointerCoords,
        0,
        0,
        1f,
        1f,
        0,
        0,
        0,
        0,
      )
    fixture.now += 100 // Increase time by 100ms
    result = sut.convert(event, recorderConfig)
    assertNotNull(result)
    assertTrue(result[0] is RRWebInteractionEvent)
    assertEquals(
      RRWebInteractionEvent.InteractionType.TouchEnd,
      (result[0] as RRWebInteractionEvent).interactionType,
    )
    assertEquals(1, (result[0] as RRWebInteractionEvent).pointerId)
    event.recycle()

    // Simulate first finger up
    event = MotionEvent.obtain(0, 4, MotionEvent.ACTION_UP, 90f, 90f, 0)
    fixture.now += 100 // Increase time by 100ms
    result = sut.convert(event, recorderConfig)
    assertNotNull(result)
    assertTrue(result[0] is RRWebInteractionEvent)
    assertEquals(
      RRWebInteractionEvent.InteractionType.TouchEnd,
      (result[0] as RRWebInteractionEvent).interactionType,
    )
    assertEquals(0, (result[0] as RRWebInteractionEvent).pointerId)
    event.recycle()
  }
}
