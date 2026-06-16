package io.sentry.sqlite

import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.SentryDateProvider
import io.sentry.SentryLongDate
import io.sentry.SentryNanotimeDate
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.util.thread.IThreadChecker
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SQLiteSpanInstrumentationTest {

  private class Fixture {

    val scopes = mock<IScopes>()
    lateinit var sentryTracer: SentryTracer
    lateinit var options: SentryOptions

    fun getSut(
      isTransactionActive: Boolean = true,
      fileName: String = ":memory:",
    ): SQLiteSpanInstrumentation {
      options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
      whenever(scopes.options).thenReturn(options)
      sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)
      if (isTransactionActive) {
        whenever(scopes.span).thenReturn(sentryTracer)
      }
      return SQLiteSpanInstrumentation.fromFileName(fileName, scopes)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `startTimestamp is ns-precise and skips date provider when parent uses SentryNanotimeDate`() {
    // Only the parent date is queued. If startTimestamp() were to call dateProvider.now(),
    // the queue would underflow and the test would fail loudly — this is what verifies the
    // optimization is in effect.
    val parentDate = SentryNanotimeDate(Date(1_000_000L), 100_000_000L)
    val sut = setUpWithNanotimeDates(parentDate)

    val start = sut.startTimestamp()

    val durationNanos = 42_000_000L
    sut.recordSpan("SELECT 1", start, durationNanos, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()

    // startTimestamp returns an already-ns-precise value, anchored to the parent's wall clock and
    // offset by elapsed System.nanoTime(). The exact ns-math is unit-tested in
    // ChildStartTimestampOrNullTest; here we verify the integration shape.
    assertIs<SentryLongDate>(span.startDate)
    assertEquals(start, span.startDate.nanoTimestamp())
    assertEquals(start + durationNanos, span.finishDate!!.nanoTimestamp())
  }

  @Test
  fun `startTimestamp falls back to date provider when parent does not use SentryNanotimeDate`() {
    val providerDate = SentryNanotimeDate(Date(2_000_000L), 200_000_000L)
    val parentSpan = mock<ISpan>()
    whenever(parentSpan.startDate).thenReturn(SentryLongDate(1_000_000_000_000_000L))
    val options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        dateProvider = SentryDateProvider { providerDate }
      }
    whenever(fixture.scopes.options).thenReturn(options)
    whenever(fixture.scopes.span).thenReturn(parentSpan)

    val sut = SQLiteSpanInstrumentation.fromFileName(":memory:", fixture.scopes)

    assertEquals(providerDate.nanoTimestamp(), sut.startTimestamp())
  }

  @Test
  fun `startTimestamp falls back to date provider when no transaction is active`() {
    val providerDate = SentryNanotimeDate(Date(2_000_000L), 200_000_000L)
    val options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        dateProvider = SentryDateProvider { providerDate }
      }
    whenever(fixture.scopes.options).thenReturn(options)
    whenever(fixture.scopes.span).thenReturn(null)

    val sut = SQLiteSpanInstrumentation.fromFileName(":memory:", fixture.scopes)

    assertEquals(providerDate.nanoTimestamp(), sut.startTimestamp())
  }

  @Test
  fun `recordSpan records a span if a transaction is active`() {
    val sut = fixture.getSut(isTransactionActive = true)
    sut.recordSpan("SELECT 1", sut.startTimestamp(), 1_000_000, SpanStatus.OK)
    assertEquals(1, fixture.sentryTracer.children.size)
  }

  @Test
  fun `recordSpan does not record a span if no transaction is active`() {
    val sut = fixture.getSut(isTransactionActive = false)
    val start = sut.startTimestamp()
    sut.recordSpan("SELECT 1", start, 1_000_000, SpanStatus.OK)
    assertEquals(0, fixture.sentryTracer.children.size)
  }

  @Test
  fun `recordSpan creates a span with correct properties`() {
    val sut = fixture.getSut()
    val start = sut.startTimestamp()
    sut.recordSpan("SELECT * FROM users", start, 1_000_000, SpanStatus.OK)

    val span = fixture.sentryTracer.children.firstOrNull()
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals("SELECT * FROM users", span.description)
    assertEquals("auto.db.sqlite", span.spanContext.origin)
    assertEquals(SpanStatus.OK, span.status)
    assertTrue(span.isFinished)
  }

  @Test
  fun `recordSpan sets finishDate equal to startDate + durationNanos`() {
    val sut = fixture.getSut()
    val start = sut.startTimestamp()
    val durationNanos = 42_000_000L

    sut.recordSpan("SELECT 1", start, durationNanos, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals(span.startDate.nanoTimestamp() + durationNanos, span.finishDate!!.nanoTimestamp())
  }

  @Test
  fun `recordSpan attaches throwable when provided`() {
    val sut = fixture.getSut()
    val start = sut.startTimestamp()
    val exception = RuntimeException("disk I/O error")

    sut.recordSpan("INSERT INTO t VALUES(1)", start, 500_000, SpanStatus.INTERNAL_ERROR, exception)

    val span = fixture.sentryTracer.children.first()
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(exception, span.throwable)
  }

  @Test
  fun `recordSpan sets db system and db name when fileName is not the in-memory sentinel`() {
    val sut = fixture.getSut(fileName = "/data/data/com.example/databases/tracks.db")
    val start = sut.startTimestamp()
    sut.recordSpan("SELECT 1", start, 1_000_000, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals("sqlite", span.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertEquals("tracks.db", span.data[SpanDataConvention.DB_NAME_KEY])
  }

  @Test
  fun `recordSpan sets db system only when fileName is the in-memory sentinel`() {
    val sut = fixture.getSut(fileName = ":memory:")
    val start = sut.startTimestamp()
    sut.recordSpan("SELECT 1", start, 1_000_000, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals("in-memory", span.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertNull(span.data[SpanDataConvention.DB_NAME_KEY])
  }

  @Test
  fun `recordSpan sets blocked_main_thread to true and attaches call stack on main thread`() {
    val sut = fixture.getSut()
    fixture.options.threadChecker = mock<IThreadChecker>()
    whenever(fixture.options.threadChecker.isMainThread).thenReturn(true)
    whenever(fixture.options.threadChecker.currentThreadName).thenReturn("main")

    sut.recordSpan("SELECT 1", sut.startTimestamp(), 1_000_000, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertTrue(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
    assertNotNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
  }

  @Test
  fun `recordSpan sets blocked_main_thread to false and does not attach a call stack on background thread`() {
    val sut = fixture.getSut()
    fixture.options.threadChecker = mock<IThreadChecker>()
    whenever(fixture.options.threadChecker.isMainThread).thenReturn(false)
    whenever(fixture.options.threadChecker.currentThreadName).thenReturn("worker")

    sut.recordSpan("SELECT 1", sut.startTimestamp(), 1_000_000, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertFalse(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
    assertNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
  }

  private fun setUpWithNanotimeDates(vararg dates: SentryNanotimeDate): SQLiteSpanInstrumentation {
    val dateQueue = ArrayDeque(dates.toList())
    val options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        dateProvider = SentryDateProvider { dateQueue.removeFirst() }
      }
    whenever(fixture.scopes.options).thenReturn(options)
    fixture.sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
    whenever(fixture.scopes.span).thenReturn(fixture.sentryTracer)
    return SQLiteSpanInstrumentation.fromFileName(":memory:", fixture.scopes)
  }
}
