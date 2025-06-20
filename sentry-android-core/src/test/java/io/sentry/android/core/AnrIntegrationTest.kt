package io.sentry.android.core

import android.content.Context
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.SentryLevel
import io.sentry.android.core.AnrIntegration.AnrHint
import io.sentry.exception.ExceptionMechanismException
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import io.sentry.util.HintUtils
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AnrIntegrationTest {
  private class Fixture {
    val context = mock<Context>()
    val scopes = mock<IScopes>()
    var options: SentryAndroidOptions = SentryAndroidOptions().apply { setLogger(mock()) }

    fun getSut(): AnrIntegration = AnrIntegration(context)
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `before each test`() {
    val sut = fixture.getSut()
    // watch dog is static and has shared state
    sut.close()
    AppState.getInstance().resetInstance()
  }

  @Test
  fun `When ANR is enabled, ANR watch dog should be started`() {
    fixture.options.executorService = ImmediateExecutorService()
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.anrWatchDog)
    assertTrue((sut.anrWatchDog as ANRWatchDog).isAlive)
  }

  @Test
  fun `ANR watch dog should be started in the executorService`() {
    fixture.options.executorService = mock()
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertNull(sut.anrWatchDog)
  }

  @Test
  fun `When ANR is disabled, ANR should not be started`() {
    val sut = fixture.getSut()
    fixture.options.isAnrEnabled = false

    sut.register(fixture.scopes, fixture.options)

    assertNull(sut.anrWatchDog)
  }

  @Test
  fun `When ANR watch dog is triggered, it should capture an error event with AnrHint`() {
    val sut = fixture.getSut()

    sut.reportANR(fixture.scopes, fixture.options, getApplicationNotResponding())

    verify(fixture.scopes)
      .captureEvent(
        check { assertEquals(SentryLevel.ERROR, it.level) },
        check<Hint> {
          val hint = HintUtils.getSentrySdkHint(it) as AnrHint
          assertEquals("anr_foreground", hint.mechanism())
        },
      )
  }

  @Test
  fun `When ANR integration is closed, watch dog should stop`() {
    val sut = fixture.getSut()
    fixture.options.executorService = ImmediateExecutorService()

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.anrWatchDog)

    sut.close()

    assertNull(sut.anrWatchDog)
  }

  @Test
  fun `when scopes is closed right after start, integration is not registered`() {
    val deferredExecutorService = DeferredExecutorService()
    val sut = fixture.getSut()
    fixture.options.executorService = deferredExecutorService
    sut.register(fixture.scopes, fixture.options)
    assertNull(sut.anrWatchDog)
    sut.close()
    deferredExecutorService.runAll()
    assertNull(sut.anrWatchDog)
  }

  @Test
  fun `When ANR watch dog is triggered, constructs exception with proper mechanism and snapshot flag`() {
    val sut = fixture.getSut()

    sut.reportANR(fixture.scopes, fixture.options, getApplicationNotResponding())

    verify(fixture.scopes)
      .captureEvent(
        check {
          val ex = it.throwableMechanism as ExceptionMechanismException
          assertTrue(ex.isSnapshot)
          assertEquals("ANR", ex.exceptionMechanism.type)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `When App is in background, it should prepend the message with 'background' and change abnormal mechanism`() {
    val sut = fixture.getSut()
    AppState.getInstance().setInBackground(true)

    sut.reportANR(fixture.scopes, fixture.options, getApplicationNotResponding())

    verify(fixture.scopes)
      .captureEvent(
        check {
          val message = it.throwable?.message
          assertTrue(message?.startsWith("Background") == true)
        },
        check<Hint> {
          val hint = HintUtils.getSentrySdkHint(it) as AnrHint
          assertEquals("anr_background", hint.mechanism())
        },
      )
  }

  private fun getApplicationNotResponding(): ApplicationNotResponding =
    ApplicationNotResponding("ApplicationNotResponding", Thread.currentThread())
}
