package io.sentry.android.core.internal.binder

import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.logger.ILoggerApi
import io.sentry.logger.SentryLogParameters
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SentryBinderAdapterTest {
  class Fixture {
    val mockedSentry = mockStatic(Sentry::class.java)
    val scopes = mock<IScopes>()
    val transaction = mock<ITransaction>()
    val span = mock<ISpan>()
    val logger = mock<ILoggerApi>()

    fun setUp(hasTransaction: Boolean = true) {
      mockedSentry.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(scopes)
      mockedSentry.`when`<Any> { Sentry.logger() }.thenReturn(logger)
      whenever(scopes.transaction).thenReturn(if (hasTransaction) transaction else null)
      whenever(transaction.startChild(any<String>(), any<String>())).thenReturn(span)
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun setUp() {
    fixture.setUp()
  }

  @AfterTest
  fun cleanup() {
    SentryBinderAdapter.setEnabled(false, false)
    fixture.mockedSentry.close()
  }

  @Test
  fun `returns no token and does nothing when both features disabled`() {
    SentryBinderAdapter.setEnabled(false, false)

    val token = SentryBinderAdapter.onCallStart("ActivityManager", "getRunningTasks")

    assertNull(token)
    verify(fixture.transaction, never()).startChild(any<String>(), any<String>())
    verifyNoInteractions(fixture.logger)
  }

  @Test
  fun `starts and finishes a span when tracing is enabled`() {
    SentryBinderAdapter.setEnabled(true, false)

    val token = SentryBinderAdapter.onCallStart("ActivityManager", "getRunningTasks")

    assertNotNull(token)
    verify(fixture.transaction).startChild(eq("binder"), eq("ActivityManager.getRunningTasks"))

    SentryBinderAdapter.onCallEnd(token)
    verify(fixture.span).finish()
  }

  @Test
  fun `does not start a span when there is no active transaction`() {
    fixture.setUp(hasTransaction = false)
    SentryBinderAdapter.setEnabled(true, false)

    SentryBinderAdapter.onCallStart("ActivityManager", "getRunningTasks")

    verify(fixture.transaction, never()).startChild(any<String>(), any<String>())
  }

  @Test
  fun `records a log when logging is enabled`() {
    SentryBinderAdapter.setEnabled(false, true)

    val token = SentryBinderAdapter.onCallStart("ActivityManager", "getRunningTasks")

    assertNull(token)
    verify(fixture.logger)
      .log(
        eq(SentryLogLevel.INFO),
        any<SentryLogParameters>(),
        eq("binder call: %s.%s"),
        eq("ActivityManager"),
        eq("getRunningTasks"),
      )
  }

  @Test
  fun `onCallEnd with null token is a no-op`() {
    SentryBinderAdapter.setEnabled(true, false)

    SentryBinderAdapter.onCallEnd(null)

    verify(fixture.span, never()).finish()
  }
}
