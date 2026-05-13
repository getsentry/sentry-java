package io.sentry.android.core

import android.content.Context
import io.sentry.AnrHeartbeatRegistry
import io.sentry.IScopes
import io.sentry.SentryLevel
import io.sentry.exception.ExceptionMechanismException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class AnrHeartbeatIntegrationTest {
  private val context = mock<Context>()
  private val scopes = mock<IScopes>()

  private fun options(
    tid: Long = 0L,
    timeoutMs: Long = 200L,
    anrEnabled: Boolean = true,
    reportInDebug: Boolean = true,
  ): SentryAndroidOptions =
    SentryAndroidOptions().apply {
      setLogger(mock())
      anrTimeoutIntervalMillis = timeoutMs
      isAnrEnabled = anrEnabled
      isAnrReportInDebug = reportInDebug
      anrThreadId = tid
    }

  @BeforeTest
  fun `before each test`() {
    AnrHeartbeatIntegration(context).close()
    AnrHeartbeatRegistry.setListener(null)
    AppState.getInstance().resetInstance()
  }

  @AfterTest
  fun `after each test`() {
    AnrHeartbeatIntegration(context).close()
    AnrHeartbeatRegistry.setListener(null)
    AppState.getInstance().resetInstance()
  }

  @Test
  fun `disabled when anrThreadId is zero`() {
    val sut = AnrHeartbeatIntegration(context)

    sut.register(scopes, options(tid = 0L))

    assertNull(sut.watchdog)
  }

  @Test
  fun `disabled when anr detection is off`() {
    val sut = AnrHeartbeatIntegration(context)

    sut.register(scopes, options(tid = 42L, anrEnabled = false))

    assertNull(sut.watchdog)
  }

  @Test
  fun `installed when anrThreadId is set`() {
    val sut = AnrHeartbeatIntegration(context)

    sut.register(scopes, options(tid = 42L))

    assertNotNull(sut.watchdog)
    assertTrue(sut.watchdog!!.isAlive)
  }

  @Test
  fun `heartbeats suppress ANR`() {
    val sut = AnrHeartbeatIntegration(context)
    sut.register(scopes, options(tid = 42L, timeoutMs = 200L))

    // Keep beating for 3x the timeout. No event should be captured.
    val running = java.util.concurrent.atomic.AtomicBoolean(true)
    val beater = Thread {
      while (running.get()) {
        AnrHeartbeatRegistry.notifyAlive()
        Thread.sleep(25)
      }
    }
    try {
      beater.start()
      Thread.sleep(700)
      verify(scopes, never()).captureEvent(any(), any<io.sentry.Hint>())
    } finally {
      running.set(false)
      beater.join(500)
    }
  }

  @Test
  fun `no heartbeats fires ANR with native thread metadata`() {
    val sut = AnrHeartbeatIntegration(context)
    sut.register(scopes, options(tid = 42L, timeoutMs = 200L))

    // Don't beat. The watchdog polls at POLLING_INTERVAL_MS and trips after timeoutMs.
    val captor = argumentCaptor<io.sentry.SentryEvent>()
    verify(scopes, timeout(3_000)).captureEvent(captor.capture(), any<io.sentry.Hint>())

    val event = captor.firstValue
    // getThrowable() unwraps the ExceptionMechanismException; use throwableMechanism for the
    // wrapper.
    val mechanism = event.throwableMechanism
    assertTrue(mechanism is ExceptionMechanismException)
    assertNull((mechanism as ExceptionMechanismException).thread) // watchdog is not the culprit
    assertNotNull(event.level)
    assertTrue(event.level == SentryLevel.ERROR)
  }

  @Test
  fun `backgrounded app does not trip ANR even without heartbeats`() {
    AppState.getInstance().setInBackground(true)

    val sut = AnrHeartbeatIntegration(context)
    sut.register(scopes, options(tid = 42L, timeoutMs = 200L))

    // Run for ~3x the timeout with no beats. Background gate must suppress detection.
    Thread.sleep(700)
    verify(scopes, never()).captureEvent(any(), any<io.sentry.Hint>())
  }

  @Test
  fun `notifyAlive routes through the registry`() {
    val sut = AnrHeartbeatIntegration(context)
    sut.register(scopes, options(tid = 42L, timeoutMs = 200L))

    val before = sut.watchdog!!.javaClass.getDeclaredField("lastHeartbeatNs")
    before.isAccessible = true
    val ts0 = before.getLong(sut.watchdog)

    Thread.sleep(15)
    AnrHeartbeatRegistry.notifyAlive()

    val ts1 = before.getLong(sut.watchdog)
    assertTrue(ts1 > ts0, "notifyAlive must update lastHeartbeatNs via the registry")
  }

  @Test
  fun `close stops watchdog and clears registry listener`() {
    val sut = AnrHeartbeatIntegration(context)
    sut.register(scopes, options(tid = 42L))
    val wd = sut.watchdog
    assertNotNull(wd)

    sut.close()

    assertNull(sut.watchdog)
    // The thread interrupt is asynchronous; give it a moment to wind down.
    wd.join(1_000)
    assertTrue(!wd.isAlive)

    // After close, notifyAlive must be a no-op (no listener registered).
    AnrHeartbeatRegistry.notifyAlive() // would throw if listener wasn't cleared
  }
}
