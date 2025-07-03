package io.sentry.android.core.performance

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.DateUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeSpanTest {
  @Test
  fun `init default state`() {
    val span = TimeSpan()

    assertTrue(span.hasNotStarted())
    assertTrue(span.hasNotStopped())

    assertFalse(span.hasStarted())
    assertFalse(span.hasStopped())
  }

  @Test
  fun `spans are compareable`() {
    // given some spans
    val spanA = TimeSpan()
    spanA.setStartedAt(1)

    val spanB = TimeSpan()
    spanA.setStartedAt(2)

    assertEquals(1, spanA.compareTo(spanB))
  }

  @Test
  fun `spans reset`() {
    val span =
      TimeSpan().apply {
        description = "Hello World"
        setStartedAt(1)
        setStoppedAt(2)
      }
    span.reset()

    assertTrue(span.hasNotStarted())
    assertTrue(span.hasNotStopped())
    assertNull(span.description)
  }

  @Test
  fun `spans description`() {
    val span = TimeSpan().apply { description = "Hello World" }
    assertEquals("Hello World", span.description)
  }

  @Test
  fun `span duration`() {
    val span =
      TimeSpan().apply {
        setStartedAt(1)
        setStoppedAt(10)
      }
    assertEquals(9, span.durationMs)
  }

  @Test
  fun `span has no duration if not started`() {
    assertEquals(0, TimeSpan().durationMs)
  }

  @Test
  fun `span has no duration if not stopped`() {
    val span = TimeSpan().apply { setStartedAt(1) }
    assertEquals(0, span.durationMs)
  }

  @Test
  fun `span unix timestamp is correctly set`() {
    val span = TimeSpan()

    span.setStartedAt(100)
    span.setStoppedAt(200)

    assertEquals(100, span.projectedStopTimestampMs - span.startTimestampMs)
    assertEquals(100, span.durationMs)
  }

  @Test
  fun `span stop time is 0 if not started`() {
    val span = TimeSpan()
    assertEquals(0, span.projectedStopTimestampMs)
    assertEquals(0.0, span.projectedStopTimestampSecs)
  }

  @Test
  fun `span start and stop time is translated correctly into seconds`() {
    val span = TimeSpan()
    span.setStartedAt(1234)
    span.setStoppedAt(1234)

    assertEquals(span.startTimestampMs / 1000.0, span.startTimestampSecs, 0.001)
    assertEquals(span.projectedStopTimestampMs / 1000.0, span.projectedStopTimestampSecs, 0.001)
  }

  @Test
  fun `span start and stop time is translated correctly into SentryDate`() {
    val span = TimeSpan()
    assertNull(span.startTimestamp)

    span.setStartedAt(1234)
    span.setStoppedAt(1234)
    assertNotNull(span.startTimestamp)

    assertEquals(
      span.startTimestampMs.toDouble(),
      DateUtils.nanosToMillis(span.startTimestamp!!.nanoTimestamp().toDouble()),
      0.001,
    )
  }

  @Test
  fun `span start starts the timespan`() {
    val span = TimeSpan()
    span.start()

    assertTrue(span.hasStarted())
    assertFalse(span.hasNotStarted())
  }

  @Test
  fun `span stop stops the timespan`() {
    val span = TimeSpan()
    span.start()

    assertFalse(span.hasStopped())

    span.stop()

    assertTrue(span.hasStopped())
    assertFalse(span.hasNotStopped())
  }

  @Test
  fun `span start uptime getter`() {
    val span = TimeSpan()
    span.setStartedAt(1234)

    assertEquals(1234, span.startUptimeMs)
  }
}
