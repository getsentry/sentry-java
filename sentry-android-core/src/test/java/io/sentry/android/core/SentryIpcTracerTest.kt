package io.sentry.android.core

import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SpanDataConvention
import io.sentry.logger.ILoggerApi
import io.sentry.logger.SentryLogParameters
import io.sentry.util.thread.IThreadChecker
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SentryIpcTracerTest {

  private class Fixture {
    val scopes: IScopes = mock()
    val options = SentryAndroidOptions().apply { dsn = "https://key@sentry.io/123" }
    val logger: ILoggerApi = mock()
    val activeSpan: ISpan = mock()
    val childSpan: ISpan = mock()
    val threadChecker: IThreadChecker = mock()

    init {
      whenever(scopes.options).thenReturn(options)
      whenever(scopes.logger()).thenReturn(logger)
      whenever(activeSpan.startChild(any<String>(), any())).thenReturn(childSpan)
      whenever(threadChecker.currentThreadSystemId()).thenReturn(42L)
      whenever(threadChecker.getCurrentThreadName()).thenReturn("test-thread")
      options.threadChecker = threadChecker
    }
  }

  private lateinit var fixture: Fixture

  @BeforeTest
  fun setup() {
    fixture = Fixture()
    SentryIpcTracer.resetForTest()
  }

  @AfterTest
  fun teardown() {
    SentryIpcTracer.resetForTest()
  }

  private fun withMockedSentry(block: () -> Unit) {
    mockStatic(Sentry::class.java).use { mocked ->
      mocked.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.scopes)
      block()
    }
  }

  @Test
  fun `both flags disabled returns DISABLED cookie and touches nothing`() {
    withMockedSentry {
      val cookie = SentryIpcTracer.onCallStart("Settings.Secure", "getString")
      assertEquals(-1, cookie)
      verify(fixture.activeSpan, never()).startChild(any<String>(), any())
      verifyNoInteractions(fixture.logger)
      assertEquals(0, SentryIpcTracer.inFlightCount())
    }
  }

  @Test
  fun `logs only emits a log with thread attributes and returns DISABLED`() {
    fixture.options.isEnableBinderLogs = true
    whenever(fixture.scopes.span).thenReturn(fixture.activeSpan)
    withMockedSentry {
      val cookie = SentryIpcTracer.onCallStart("Settings.Secure", "getString")
      assertEquals(-1, cookie)
      verify(fixture.logger)
        .log(
          eq(SentryLogLevel.INFO),
          any<SentryLogParameters>(),
          eq("Binder IPC %s.%s"),
          eq("Settings.Secure"),
          eq("getString"),
        )
      verify(fixture.activeSpan, never()).startChild(any<String>(), any())
    }
  }

  @Test
  fun `tracing enabled without active span does not create a span`() {
    fixture.options.isEnableBinderTracing = true
    whenever(fixture.scopes.span).thenReturn(null)
    withMockedSentry {
      val cookie = SentryIpcTracer.onCallStart("Settings.Secure", "getString")
      assertEquals(-1, cookie)
      assertEquals(0, SentryIpcTracer.inFlightCount())
    }
  }

  @Test
  fun `tracing enabled starts a child span with thread data and onCallEnd finishes it`() {
    fixture.options.isEnableBinderTracing = true
    whenever(fixture.scopes.span).thenReturn(fixture.activeSpan)
    withMockedSentry {
      val cookie = SentryIpcTracer.onCallStart("Settings.Secure", "getString")
      assertNotEquals(-1, cookie)
      verify(fixture.activeSpan).startChild(eq("binder.ipc"), eq("Settings.Secure.getString"))
      verify(fixture.childSpan).setData(eq(SpanDataConvention.THREAD_ID), eq("42"))
      verify(fixture.childSpan).setData(eq(SpanDataConvention.THREAD_NAME), eq("test-thread"))
      assertEquals(1, SentryIpcTracer.inFlightCount())

      SentryIpcTracer.onCallEnd(cookie)
      verify(fixture.childSpan).finish()
      assertEquals(0, SentryIpcTracer.inFlightCount())
    }
  }

  @Test
  fun `both flags enabled emits log and creates span`() {
    fixture.options.isEnableBinderTracing = true
    fixture.options.isEnableBinderLogs = true
    whenever(fixture.scopes.span).thenReturn(fixture.activeSpan)
    withMockedSentry {
      val cookie = SentryIpcTracer.onCallStart("ContentResolver", "query")
      assertNotEquals(-1, cookie)
      verify(fixture.logger)
        .log(
          eq(SentryLogLevel.INFO),
          any<SentryLogParameters>(),
          eq("Binder IPC %s.%s"),
          eq("ContentResolver"),
          eq("query"),
        )
      verify(fixture.activeSpan).startChild(eq("binder.ipc"), eq("ContentResolver.query"))

      SentryIpcTracer.onCallEnd(cookie)
      verify(fixture.childSpan).finish()
    }
  }

  @Test
  fun `onCallEnd with DISABLED cookie is a no-op`() {
    SentryIpcTracer.onCallEnd(-1)
  }

  @Test
  fun `onCallEnd with unknown cookie is a no-op`() {
    SentryIpcTracer.onCallEnd(9999)
    assertEquals(0, SentryIpcTracer.inFlightCount())
  }

  @Test
  fun `onCallStart swallows exceptions and returns DISABLED`() {
    fixture.options.isEnableBinderTracing = true
    whenever(fixture.scopes.span).thenReturn(fixture.activeSpan)
    whenever(fixture.activeSpan.startChild(any<String>(), any()))
      .thenThrow(RuntimeException("boom"))
    withMockedSentry {
      val cookie = SentryIpcTracer.onCallStart("ContentResolver", "query")
      assertEquals(-1, cookie)
      assertEquals(0, SentryIpcTracer.inFlightCount())
    }
  }

  @Test
  fun `onCallEnd swallows exceptions`() {
    fixture.options.isEnableBinderTracing = true
    whenever(fixture.scopes.span).thenReturn(fixture.activeSpan)
    whenever(fixture.childSpan.finish()).thenThrow(RuntimeException("boom"))
    withMockedSentry {
      val cookie = SentryIpcTracer.onCallStart("ContentResolver", "query")
      SentryIpcTracer.onCallEnd(cookie)
      assertEquals(0, SentryIpcTracer.inFlightCount())
    }
  }

  @Test
  fun `non-android options returns DISABLED even when flags would be on`() {
    val coreOptions = io.sentry.SentryOptions().apply { dsn = "https://key@sentry.io/123" }
    whenever(fixture.scopes.options).thenReturn(coreOptions)
    withMockedSentry {
      val cookie = SentryIpcTracer.onCallStart("ContentResolver", "query")
      assertEquals(-1, cookie)
    }
  }

  @Test
  fun `nested calls produce distinct cookies and finish independently`() {
    fixture.options.isEnableBinderTracing = true
    val innerChild: ISpan = mock()
    whenever(fixture.childSpan.startChild(any<String>(), any())).thenReturn(innerChild)
    whenever(fixture.scopes.span).thenReturn(fixture.activeSpan, fixture.childSpan)

    withMockedSentry {
      val outer = SentryIpcTracer.onCallStart("ContentResolver", "query")
      val inner = SentryIpcTracer.onCallStart("PackageManager", "getPackageInfo")
      assertNotEquals(outer, inner)
      assertEquals(2, SentryIpcTracer.inFlightCount())

      SentryIpcTracer.onCallEnd(inner)
      verify(innerChild).finish()
      assertEquals(1, SentryIpcTracer.inFlightCount())

      SentryIpcTracer.onCallEnd(outer)
      verify(fixture.childSpan).finish()
      assertEquals(0, SentryIpcTracer.inFlightCount())
    }
  }
}
