package io.sentry.sqlite

import io.sentry.IScopes
import io.sentry.SentryDateProvider
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

  @Test
  fun `recordSpan repairs start precision when parent uses SentryNanotimeDate`() {
    val sameMillis = Date(1_000_000L)
    val parentNanos = 100_000_000L
    val childNanos = 100_500_000L

    val sut =
      setUpWithNanotimeDates(
        SentryNanotimeDate(sameMillis, parentNanos),
        SentryNanotimeDate(sameMillis, childNanos),
      )
    val start = sut.startTimestamp()
    val durationNanos = 42_000_000L

    sut.recordSpan("SELECT 1", start, durationNanos, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()

    val parentStart = fixture.sentryTracer.startDate
    val expectedStart = parentStart.laterDateNanosTimestampByDiff(start)
    assertEquals(expectedStart, span.startDate.nanoTimestamp())
    assertEquals(expectedStart + durationNanos, span.finishDate!!.nanoTimestamp())
  }

  @Test
  fun `recordSpan gives distinct ordered starts within the same millisecond`() {
    val sameMillis = Date(1_000_000L)
    val parentNanos = 100_000_000L
    val child1Nanos = 100_200_000L
    val child2Nanos = 100_800_000L

    val sut =
      setUpWithNanotimeDates(
        SentryNanotimeDate(sameMillis, parentNanos),
        SentryNanotimeDate(sameMillis, child1Nanos),
        SentryNanotimeDate(sameMillis, child2Nanos),
      )
    val start1 = sut.startTimestamp()
    val start2 = sut.startTimestamp()

    assertEquals(
      start1.nanoTimestamp(),
      start2.nanoTimestamp(),
      "Raw starts share the same ms-quantized timestamp",
    )

    sut.recordSpan("SELECT 1", start1, 1_000_000, SpanStatus.OK)
    sut.recordSpan("SELECT 2", start2, 1_000_000, SpanStatus.OK)

    val span1 = fixture.sentryTracer.children[0]
    val span2 = fixture.sentryTracer.children[1]

    assertTrue(
      span1.startDate.nanoTimestamp() < span2.startDate.nanoTimestamp(),
      "Repaired starts should be distinct and ordered",
    )
  }

  @Test
  fun `recordSpan preserves exact duration after precision repair`() {
    val sameMillis = Date(1_000_000L)
    val sut =
      setUpWithNanotimeDates(
        SentryNanotimeDate(sameMillis, 100_000_000L),
        SentryNanotimeDate(sameMillis, 100_750_000L),
      )
    val start = sut.startTimestamp()
    val durationNanos = 123_456L

    sut.recordSpan("SELECT 1", start, durationNanos, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    val actualDuration = span.finishDate!!.nanoTimestamp() - span.startDate.nanoTimestamp()
    assertEquals(durationNanos, actualDuration)
  }

  @Test
  fun `recordSpan does not repair start when parent is not SentryNanotimeDate`() {
    val sut = fixture.getSut(isTransactionActive = true)
    val start = sut.startTimestamp()
    val durationNanos = 1_000_000L

    sut.recordSpan("SELECT 1", start, durationNanos, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals(start.nanoTimestamp(), span.startDate.nanoTimestamp())
    assertEquals(start.nanoTimestamp() + durationNanos, span.finishDate!!.nanoTimestamp())
  }

  @Test
  fun `recordCoarseSpan records a span if a transaction is active`() {
    val sut = fixture.getSut(isTransactionActive = true)
    sut.recordCoarseSpan("SELECT 1", sut.startTimestamp(), SpanStatus.OK)
    assertEquals(1, fixture.sentryTracer.children.size)
  }

  @Test
  fun `recordCoarseSpan does not record a span if no transaction is active`() {
    val sut = fixture.getSut(isTransactionActive = false)
    val start = sut.startTimestamp()
    sut.recordCoarseSpan("SELECT 1", start, SpanStatus.OK)
    assertEquals(0, fixture.sentryTracer.children.size)
  }

  @Test
  fun `recordCoarseSpan creates a span with correct properties`() {
    val sut = fixture.getSut()
    val start = sut.startTimestamp()
    sut.recordCoarseSpan("SELECT * FROM users", start, SpanStatus.OK)

    val span = fixture.sentryTracer.children.firstOrNull()
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals("SELECT * FROM users", span.description)
    assertEquals("auto.db.sqlite", span.spanContext.origin)
    assertEquals(SpanStatus.OK, span.status)
    assertTrue(span.isFinished)
  }

  @Test
  fun `recordCoarseSpan finishes the span at the time of invocation`() {
    val sut = fixture.getSut()
    val start = sut.startTimestamp()

    sut.recordCoarseSpan("SELECT 1", start, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertTrue(span.isFinished)
    assertEquals(SpanStatus.OK, span.status)
    // Unlike the duration overload, no synthetic end timestamp is supplied; the span finishes at
    // "now", i.e. at or after its start.
    assertTrue(span.finishDate!!.nanoTimestamp() >= start.nanoTimestamp())
  }

  @Test
  fun `recordCoarseSpan attaches throwable when provided`() {
    val sut = fixture.getSut()
    val start = sut.startTimestamp()
    val exception = RuntimeException("disk I/O error")

    sut.recordCoarseSpan("INSERT INTO t VALUES(1)", start, SpanStatus.INTERNAL_ERROR, exception)

    val span = fixture.sentryTracer.children.first()
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(exception, span.throwable)
  }

  @Test
  fun `recordCoarseSpan sets db system and db name when fileName is not the in-memory sentinel`() {
    val sut = fixture.getSut(fileName = "/data/data/com.example/databases/tracks.db")
    val start = sut.startTimestamp()
    sut.recordCoarseSpan("SELECT 1", start, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals("sqlite", span.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertEquals("tracks.db", span.data[SpanDataConvention.DB_NAME_KEY])
  }

  @Test
  fun `recordCoarseSpan sets db system only when fileName is the in-memory sentinel`() {
    val sut = fixture.getSut(fileName = ":memory:")
    val start = sut.startTimestamp()
    sut.recordCoarseSpan("SELECT 1", start, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals("in-memory", span.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertNull(span.data[SpanDataConvention.DB_NAME_KEY])
  }

  @Test
  fun `recordCoarseSpan sets blocked_main_thread to true and attaches call stack on main thread`() {
    val sut = fixture.getSut()
    fixture.options.threadChecker = mock<IThreadChecker>()
    whenever(fixture.options.threadChecker.isMainThread).thenReturn(true)
    whenever(fixture.options.threadChecker.currentThreadName).thenReturn("main")

    sut.recordCoarseSpan("SELECT 1", sut.startTimestamp(), SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertTrue(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
    assertNotNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
  }

  @Test
  fun `recordCoarseSpan sets blocked_main_thread to false and does not attach a call stack on background thread`() {
    val sut = fixture.getSut()
    fixture.options.threadChecker = mock<IThreadChecker>()
    whenever(fixture.options.threadChecker.isMainThread).thenReturn(false)
    whenever(fixture.options.threadChecker.currentThreadName).thenReturn("worker")

    sut.recordCoarseSpan("SELECT 1", sut.startTimestamp(), SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertFalse(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
    assertNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
  }

  @Test
  fun `recordCoarseSpan does not repair start precision when parent uses SentryNanotimeDate`() {
    val sameMillis = Date(1_000_000L)
    val parentNanos = 100_000_000L
    val childNanos = 100_500_000L

    val finishNanos = 100_900_000L
    val sut =
      setUpWithNanotimeDates(
        SentryNanotimeDate(sameMillis, parentNanos),
        SentryNanotimeDate(sameMillis, childNanos),
        SentryNanotimeDate(sameMillis, finishNanos),
      )
    val start = sut.startTimestamp()

    sut.recordCoarseSpan("SELECT 1", start, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals(start.nanoTimestamp(), span.startDate.nanoTimestamp())
    assertTrue(span.finishDate!!.nanoTimestamp() >= span.startDate.nanoTimestamp())
  }

  @Test
  fun `recordCoarseSpan does not repair start when parent is not SentryNanotimeDate`() {
    val sut = fixture.getSut(isTransactionActive = true)
    val start = sut.startTimestamp()

    sut.recordCoarseSpan("SELECT 1", start, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals(start.nanoTimestamp(), span.startDate.nanoTimestamp())
    assertTrue(span.finishDate!!.nanoTimestamp() >= start.nanoTimestamp())
  }

  @Test
  fun `fromFileName sets db name from fileName when using recordSpan`() {
    val options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
    whenever(fixture.scopes.options).thenReturn(options)
    fixture.sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
    whenever(fixture.scopes.span).thenReturn(fixture.sentryTracer)

    val sut = SQLiteSpanInstrumentation.fromFileName("tracks.db", fixture.scopes)
    val start = sut.startTimestamp()
    sut.recordSpan("SELECT 1", start, 1_000_000, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals("sqlite", span.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertEquals("tracks.db", span.data[SpanDataConvention.DB_NAME_KEY])
  }

  @Test
  fun `fromDatabaseName sets db name from databaseName when using recordSpan`() {
    val options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
    whenever(fixture.scopes.options).thenReturn(options)
    fixture.sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
    whenever(fixture.scopes.span).thenReturn(fixture.sentryTracer)

    val sut = SQLiteSpanInstrumentation.fromDatabaseName("tracks.db", fixture.scopes)
    val start = sut.startTimestamp()
    sut.recordSpan("SELECT 1", start, 1_000_000, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals("sqlite", span.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertEquals("tracks.db", span.data[SpanDataConvention.DB_NAME_KEY])
  }

  @Test
  fun `fromFileName sets db name from fileName when using recordCoarseSpan`() {
    val options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
    whenever(fixture.scopes.options).thenReturn(options)
    fixture.sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
    whenever(fixture.scopes.span).thenReturn(fixture.sentryTracer)

    val sut = SQLiteSpanInstrumentation.fromFileName("tracks.db", fixture.scopes)
    val start = sut.startTimestamp()
    sut.recordCoarseSpan("SELECT 1", start, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals("sqlite", span.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertEquals("tracks.db", span.data[SpanDataConvention.DB_NAME_KEY])
  }

  @Test
  fun `fromDatabaseName sets db name from databaseName when using recordCoarseSpan`() {
    val options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
    whenever(fixture.scopes.options).thenReturn(options)
    fixture.sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
    whenever(fixture.scopes.span).thenReturn(fixture.sentryTracer)

    val sut = SQLiteSpanInstrumentation.fromDatabaseName("tracks.db", fixture.scopes)
    val start = sut.startTimestamp()
    sut.recordCoarseSpan("SELECT 1", start, SpanStatus.OK)

    val span = fixture.sentryTracer.children.first()
    assertEquals("sqlite", span.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertEquals("tracks.db", span.data[SpanDataConvention.DB_NAME_KEY])
  }

  // --- Transaction tracking tests ---

  @Test
  fun `transaction tracking nests queries under a BEGIN parent`() {
    val sut = fixture.getSut()
    val t0 = sut.startTimestamp()
    sut.recordSpanWithTransactionTracking("BEGIN IMMEDIATE TRANSACTION", t0, 100, SpanStatus.OK)
    val t1 = sut.startTimestamp()
    sut.recordSpanWithTransactionTracking("INSERT INTO song VALUES (?)", t1, 200, SpanStatus.OK)
    val t2 = sut.startTimestamp()
    sut.recordSpanWithTransactionTracking("END TRANSACTION", t2, 150, SpanStatus.OK)

    val directChildren =
      fixture.sentryTracer.children.filter {
        it.parentSpanId == fixture.sentryTracer.spanContext.spanId
      }
    assertEquals(1, directChildren.size)
    val txnSpan = directChildren[0]
    assertEquals("db.sql.transaction", txnSpan.operation)
    assertEquals("BEGIN IMMEDIATE TRANSACTION", txnSpan.description)
    assertEquals(SpanStatus.OK, txnSpan.status)
    assertTrue(txnSpan.isFinished)

    val txnChildren =
      fixture.sentryTracer.children.filter { it.parentSpanId == txnSpan.spanContext.spanId }
    assertEquals(2, txnChildren.size)
    assertEquals("db.sql.query", txnChildren[0].operation)
    assertEquals("INSERT INTO song VALUES (?)", txnChildren[0].description)
    assertEquals("db.sql.transaction.commit", txnChildren[1].operation)
    assertEquals("END TRANSACTION", txnChildren[1].description)
  }

  @Test
  fun `transaction tracking creates rollback leaf with ABORTED status on ROLLBACK`() {
    val sut = fixture.getSut()
    sut.recordSpanWithTransactionTracking(
      "BEGIN IMMEDIATE TRANSACTION",
      sut.startTimestamp(),
      100,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "INSERT INTO song VALUES (?)",
      sut.startTimestamp(),
      200,
      SpanStatus.INTERNAL_ERROR,
      RuntimeException("constraint"),
    )
    sut.recordSpanWithTransactionTracking(
      "ROLLBACK TRANSACTION",
      sut.startTimestamp(),
      150,
      SpanStatus.OK,
    )

    val directChildren =
      fixture.sentryTracer.children.filter {
        it.parentSpanId == fixture.sentryTracer.spanContext.spanId
      }
    val txnSpan = directChildren[0]
    assertEquals("db.sql.transaction", txnSpan.operation)
    assertEquals(SpanStatus.ABORTED, txnSpan.status)
    assertTrue(txnSpan.isFinished)

    val txnChildren =
      fixture.sentryTracer.children.filter { it.parentSpanId == txnSpan.spanContext.spanId }
    assertEquals(2, txnChildren.size)
    assertEquals("db.sql.transaction.rollback", txnChildren[1].operation)
    assertEquals("ROLLBACK TRANSACTION", txnChildren[1].description)
  }

  @Test
  fun `transaction tracking treats SAVEPOINT and ROLLBACK TO as pass-through queries`() {
    val sut = fixture.getSut()
    sut.recordSpanWithTransactionTracking(
      "BEGIN IMMEDIATE TRANSACTION",
      sut.startTimestamp(),
      100,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking("SAVEPOINT '1'", sut.startTimestamp(), 50, SpanStatus.OK)
    sut.recordSpanWithTransactionTracking(
      "INSERT INTO song VALUES (?)",
      sut.startTimestamp(),
      200,
      SpanStatus.INTERNAL_ERROR,
      RuntimeException("constraint"),
    )
    sut.recordSpanWithTransactionTracking(
      "ROLLBACK TRANSACTION TO SAVEPOINT '1'",
      sut.startTimestamp(),
      80,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "INSERT INTO song VALUES (?)",
      sut.startTimestamp(),
      200,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "END TRANSACTION",
      sut.startTimestamp(),
      150,
      SpanStatus.OK,
    )

    val directChildren =
      fixture.sentryTracer.children.filter {
        it.parentSpanId == fixture.sentryTracer.spanContext.spanId
      }
    val txnSpan = directChildren[0]
    assertEquals(SpanStatus.OK, txnSpan.status)

    val txnChildren =
      fixture.sentryTracer.children.filter { it.parentSpanId == txnSpan.spanContext.spanId }
    assertEquals(5, txnChildren.size)
    assertEquals("db.sql.query", txnChildren[0].operation) // SAVEPOINT
    assertEquals("SAVEPOINT '1'", txnChildren[0].description)
    assertEquals("db.sql.query", txnChildren[2].operation) // ROLLBACK TO
    assertEquals("ROLLBACK TRANSACTION TO SAVEPOINT '1'", txnChildren[2].description)
    assertEquals("db.sql.transaction.commit", txnChildren[4].operation)
  }

  @Test
  fun `finishDanglingTransaction closes open transaction with ABORTED and connection-closed leaf`() {
    val sut = fixture.getSut()
    sut.recordSpanWithTransactionTracking(
      "BEGIN IMMEDIATE TRANSACTION",
      sut.startTimestamp(),
      100,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "INSERT INTO song VALUES (?)",
      sut.startTimestamp(),
      200,
      SpanStatus.OK,
    )
    sut.finishDanglingTransaction()

    val directChildren =
      fixture.sentryTracer.children.filter {
        it.parentSpanId == fixture.sentryTracer.spanContext.spanId
      }
    val txnSpan = directChildren[0]
    assertEquals(SpanStatus.ABORTED, txnSpan.status)
    assertTrue(txnSpan.isFinished)

    val txnChildren =
      fixture.sentryTracer.children.filter { it.parentSpanId == txnSpan.spanContext.spanId }
    assertEquals(2, txnChildren.size)
    assertEquals("db.sql.transaction.rollback", txnChildren[1].operation)
    assertEquals("(connection closed)", txnChildren[1].description)
  }

  @Test
  fun `finishDanglingTransaction is a no-op when no transaction is open`() {
    val sut = fixture.getSut()
    sut.finishDanglingTransaction()
    assertEquals(0, fixture.sentryTracer.children.size)
  }

  @Test
  fun `double BEGIN finishes existing transaction ABORTED and opens a new one`() {
    val sut = fixture.getSut()
    sut.recordSpanWithTransactionTracking(
      "BEGIN IMMEDIATE TRANSACTION",
      sut.startTimestamp(),
      100,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "INSERT INTO song VALUES (?)",
      sut.startTimestamp(),
      200,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "BEGIN IMMEDIATE TRANSACTION",
      sut.startTimestamp(),
      100,
      SpanStatus.OK,
    )

    val directChildren =
      fixture.sentryTracer.children.filter {
        it.parentSpanId == fixture.sentryTracer.spanContext.spanId
      }
    assertEquals(2, directChildren.size)

    val firstTxn = directChildren[0]
    assertEquals(SpanStatus.ABORTED, firstTxn.status)
    assertTrue(firstTxn.isFinished)

    val secondTxn = directChildren[1]
    assertEquals("db.sql.transaction", secondTxn.operation)
    assertFalse(secondTxn.isFinished)
  }

  @Test
  fun `failed BEGIN records db_sql_query with error and does not open transaction`() {
    val sut = fixture.getSut()
    sut.recordSpanWithTransactionTracking(
      "BEGIN IMMEDIATE TRANSACTION",
      sut.startTimestamp(),
      100,
      SpanStatus.INTERNAL_ERROR,
      RuntimeException("SQLITE_BUSY"),
    )

    val allChildren = fixture.sentryTracer.children
    assertEquals(1, allChildren.size)
    val span = allChildren[0]
    assertEquals("db.sql.query", span.operation)
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(fixture.sentryTracer.spanContext.spanId, span.parentSpanId)

    sut.recordSpanWithTransactionTracking("SELECT 1", sut.startTimestamp(), 50, SpanStatus.OK)
    assertEquals(2, fixture.sentryTracer.children.size)
    assertEquals(
      fixture.sentryTracer.spanContext.spanId,
      fixture.sentryTracer.children[1].parentSpanId,
    )
  }

  @Test
  fun `failed COMMIT records error child but keeps transaction open`() {
    val sut = fixture.getSut()
    sut.recordSpanWithTransactionTracking(
      "BEGIN IMMEDIATE TRANSACTION",
      sut.startTimestamp(),
      100,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "END TRANSACTION",
      sut.startTimestamp(),
      150,
      SpanStatus.INTERNAL_ERROR,
      RuntimeException("disk full"),
    )

    val directChildren =
      fixture.sentryTracer.children.filter {
        it.parentSpanId == fixture.sentryTracer.spanContext.spanId
      }
    val txnSpan = directChildren[0]
    assertFalse(txnSpan.isFinished)

    val txnChildren =
      fixture.sentryTracer.children.filter { it.parentSpanId == txnSpan.spanContext.spanId }
    assertEquals(1, txnChildren.size)
    assertEquals("db.sql.query", txnChildren[0].operation)
    assertEquals(SpanStatus.INTERNAL_ERROR, txnChildren[0].status)
  }

  @Test
  fun `failed ROLLBACK records error child but keeps transaction open`() {
    val sut = fixture.getSut()
    sut.recordSpanWithTransactionTracking(
      "BEGIN IMMEDIATE TRANSACTION",
      sut.startTimestamp(),
      100,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "ROLLBACK",
      sut.startTimestamp(),
      150,
      SpanStatus.INTERNAL_ERROR,
      RuntimeException("error"),
    )

    val directChildren =
      fixture.sentryTracer.children.filter {
        it.parentSpanId == fixture.sentryTracer.spanContext.spanId
      }
    val txnSpan = directChildren[0]
    assertFalse(txnSpan.isFinished)

    val txnChildren =
      fixture.sentryTracer.children.filter { it.parentSpanId == txnSpan.spanContext.spanId }
    assertEquals(1, txnChildren.size)
    assertEquals("db.sql.query", txnChildren[0].operation)
    assertEquals(SpanStatus.INTERNAL_ERROR, txnChildren[0].status)
  }

  @Test
  fun `transaction tracking skips everything when no Sentry span is active`() {
    val sut = fixture.getSut(isTransactionActive = false)
    sut.recordSpanWithTransactionTracking(
      "BEGIN IMMEDIATE TRANSACTION",
      sut.startTimestamp(),
      100,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "INSERT INTO song VALUES (?)",
      sut.startTimestamp(),
      200,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "END TRANSACTION",
      sut.startTimestamp(),
      150,
      SpanStatus.OK,
    )
    assertEquals(0, fixture.sentryTracer.children.size)
  }

  @Test
  fun `query outside transaction is a direct child of scopes span`() {
    val sut = fixture.getSut()
    sut.recordSpanWithTransactionTracking(
      "SELECT * FROM song",
      sut.startTimestamp(),
      200,
      SpanStatus.OK,
    )

    assertEquals(1, fixture.sentryTracer.children.size)
    val span = fixture.sentryTracer.children[0]
    assertEquals("db.sql.query", span.operation)
    assertEquals(fixture.sentryTracer.spanContext.spanId, span.parentSpanId)
  }

  @Test
  fun `orphaned COMMIT without open transaction records as ordinary query`() {
    val sut = fixture.getSut()
    sut.recordSpanWithTransactionTracking("COMMIT", sut.startTimestamp(), 100, SpanStatus.OK)

    assertEquals(1, fixture.sentryTracer.children.size)
    val span = fixture.sentryTracer.children[0]
    assertEquals("db.sql.query", span.operation)
    assertEquals("COMMIT", span.description)
  }

  @Test
  fun `orphaned ROLLBACK without open transaction records as ordinary query`() {
    val sut = fixture.getSut()
    sut.recordSpanWithTransactionTracking("ROLLBACK", sut.startTimestamp(), 100, SpanStatus.OK)

    assertEquals(1, fixture.sentryTracer.children.size)
    val span = fixture.sentryTracer.children[0]
    assertEquals("db.sql.query", span.operation)
    assertEquals("ROLLBACK", span.description)
  }

  @Test
  fun `transaction parent span carries db metadata`() {
    val sut = fixture.getSut(fileName = "/data/data/com.example/databases/tracks.db")
    sut.recordSpanWithTransactionTracking(
      "BEGIN IMMEDIATE TRANSACTION",
      sut.startTimestamp(),
      100,
      SpanStatus.OK,
    )
    sut.recordSpanWithTransactionTracking(
      "END TRANSACTION",
      sut.startTimestamp(),
      150,
      SpanStatus.OK,
    )

    val txnSpan = fixture.sentryTracer.children[0]
    assertEquals("sqlite", txnSpan.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertEquals("tracks.db", txnSpan.data[SpanDataConvention.DB_NAME_KEY])
    assertEquals("auto.db.sqlite", txnSpan.spanContext.origin)
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
