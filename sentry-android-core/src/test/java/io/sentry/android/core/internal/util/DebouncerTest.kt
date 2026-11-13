package io.sentry.android.core.internal.util

import io.sentry.time.TestMonotonicTicker
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class DebouncerTest {
  private class Fixture {
    val ticker = TestMonotonicTicker()

    fun advance(millis: Long) = ticker.advance(millis, TimeUnit.MILLISECONDS)

    fun getDebouncer(waitTimeMs: Long = 3000, maxExecutions: Int = 1): Debouncer =
      Debouncer(ticker, waitTimeMs, maxExecutions)
  }

  private val fixture = Fixture()

  @Test
  fun `Debouncer should not debounce on the first check`() {
    val debouncer = fixture.getDebouncer()
    assertFalse(debouncer.checkForDebounce())
  }

  @Test
  fun `Debouncer should not debounce if wait time is 0`() {
    val debouncer = fixture.getDebouncer(0)
    assertFalse(debouncer.checkForDebounce())
    assertFalse(debouncer.checkForDebounce())
    assertFalse(debouncer.checkForDebounce())
  }

  @Test
  fun `Debouncer should signal debounce if the second invocation is too early`() {
    val debouncer = fixture.getDebouncer(3000)
    assertFalse(debouncer.checkForDebounce())

    fixture.advance(2999)
    assertTrue(debouncer.checkForDebounce())
  }

  @Test
  fun `Debouncer should not signal debounce if the second invocation is late enough`() {
    val debouncer = fixture.getDebouncer(3000)
    assertFalse(debouncer.checkForDebounce())

    fixture.advance(3000)
    assertFalse(debouncer.checkForDebounce())
  }

  @Test
  fun `Debouncer does not debounce after a window that expired during deep sleep`() {
    val debouncer = fixture.getDebouncer(60_000)
    assertFalse(debouncer.checkForDebounce())

    // The ticker keeps counting while the device sleeps, so an hour asleep expires the window.
    fixture.advance(TimeUnit.HOURS.toMillis(1))
    assertFalse(debouncer.checkForDebounce())
  }

  @Test
  fun `Debouncer maxExecutions is always greater than 0`() {
    val debouncer = fixture.getDebouncer(3000, -1)
    assertFalse(debouncer.checkForDebounce())
    assertTrue(debouncer.checkForDebounce())
  }

  @Test
  fun `Debouncer should signal debounce after maxExecutions calls`() {
    val debouncer = fixture.getDebouncer(3000, 3)
    assertFalse(debouncer.checkForDebounce())
    assertFalse(debouncer.checkForDebounce())
    assertFalse(debouncer.checkForDebounce())
    assertTrue(debouncer.checkForDebounce())
  }

  @Test
  fun `Debouncer maxExecutions counter resets if the other invocation is late enough`() {
    val debouncer = fixture.getDebouncer(3000, 3)
    assertFalse(debouncer.checkForDebounce())
    assertFalse(debouncer.checkForDebounce())

    // After waitTimeMs passes, the maxExecutions counter is reset
    fixture.advance(3000)
    assertFalse(debouncer.checkForDebounce())
    assertFalse(debouncer.checkForDebounce())
    assertFalse(debouncer.checkForDebounce())
    assertTrue(debouncer.checkForDebounce())
  }

  @Test
  fun `Debouncer maxExecutions counter resets after maxExecutions`() {
    val debouncer = fixture.getDebouncer(3000, 3)
    assertFalse(debouncer.checkForDebounce())
    assertFalse(debouncer.checkForDebounce())
    assertFalse(debouncer.checkForDebounce())
    assertTrue(debouncer.checkForDebounce())
    assertFalse(debouncer.checkForDebounce())
  }
}
