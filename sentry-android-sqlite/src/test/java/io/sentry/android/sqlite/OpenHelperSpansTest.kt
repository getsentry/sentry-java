package io.sentry.android.sqlite

import android.database.CrossProcessCursor
import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.sentry.IScopes
import io.sentry.NoOpSpan
import io.sentry.SentryDateProvider
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLongDate
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.util.sql.SqlQueryAnalyzer
import io.sentry.util.thread.IThreadChecker
import io.sentry.vendor.otel.sql.OtelSqlSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenHelperSpansTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    lateinit var sentryTracer: SentryTracer
    lateinit var options: SentryOptions

    fun getSut(isSpanActive: Boolean = true, databaseName: String? = null): OpenHelperSpans {
      options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
      whenever(scopes.options).thenReturn(options)
      sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)

      if (isSpanActive) {
        whenever(scopes.span).thenReturn(sentryTracer)
      }
      return OpenHelperSpans(scopes, databaseName)
    }
  }

  private val fixture = Fixture()

  @Before
  fun setup() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  @Test
  fun `add SQLite to the list of integrations`() {
    assertFalse(SentryIntegrationPackageStorage.getInstance().integrations.contains("SQLite"))
    fixture.getSut()
    assertTrue(SentryIntegrationPackageStorage.getInstance().integrations.contains("SQLite"))
  }

  @Test
  fun `performSql creates a span if a span is running`() {
    val sut = fixture.getSut()
    sut.performSql("sql") {}
    val span = fixture.sentryTracer.children.firstOrNull()
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals("auto.db.sqlite", span.spanContext.origin)
    assertEquals("sql", span.description)
    assertEquals(SpanStatus.OK, span.status)
    assertTrue(span.isFinished)
  }

  @Test
  fun `performSql describes the span with the sanitized query`() {
    // The analyzer treats double quotes in SQLite as strings today; the expected text follows if
    // that changes.
    val sql = "SELECT a FROM t WHERE p = \"secret\""
    val expected = SqlQueryAnalyzer.analyze(sql, "sqlite", LIMIT)
    assertThat(expected.querySummary).isNotNull()

    listOf<() -> Unit>({}, { throw SQLException() }).forEach { operation ->
      val sut = fixture.getSut(databaseName = "tracks.db")

      runCatching { sut.performSql(sql, operation) }

      val span = fixture.sentryTracer.children.single()
      assertWithMessage("${span.status}").that(span.description).isEqualTo(expected.queryText)
      assertWithMessage("${span.status}")
        .that(span.data)
        .containsAtLeast(
          SpanDataConvention.DB_QUERY_TEXT_KEY,
          expected.queryText,
          SpanDataConvention.DB_QUERY_SUMMARY_KEY,
          expected.querySummary,
        )
    }
  }

  @Test
  fun `performSql ends the span when the database call returns or throws, before the analysis`() {
    var nowNanos = 0L
    listOf<() -> Unit>(
        { nowNanos += QUERY_NANOS },
        {
          nowNanos += QUERY_NANOS
          throw SQLException()
        },
      )
      .forEach { operation ->
        val sut = fixture.getSut(databaseName = "tracks.db")
        nowNanos = 1_000_000_000L
        fixture.options.dateProvider = SentryDateProvider { SentryLongDate(nowNanos) }

        Mockito.mockStatic(OtelSqlSanitizer::class.java, Mockito.CALLS_REAL_METHODS).use { sanitizer
          ->
          sanitizer
            .`when`<Any> { OtelSqlSanitizer.sanitize(any(), any()) }
            .thenAnswer {
              nowNanos += 3_000_000L
              it.callRealMethod()
            }

          runCatching { sut.performSql("SELECT a FROM t", operation) }
        }

        val span = fixture.sentryTracer.children.single()
        assertWithMessage("${span.status}")
          .that(span.startDate.nanoTimestamp())
          .isEqualTo(1_000_000_000L)
        assertWithMessage("${span.status}")
          .that(span.finishDate?.nanoTimestamp())
          .isEqualTo(1_000_000_000L + QUERY_NANOS)
      }
  }

  @Test
  fun `performSql limits the sanitized query to 8 KiB`() {
    val sql = "SELECT a FROM t WHERE " + (0 until 1000).joinToString(" OR ") { "c$it IS NULL" }
    val expected = SqlQueryAnalyzer.analyze(sql, "sqlite", LIMIT).queryText
    assertThat(expected).isNotEqualTo(SqlQueryAnalyzer.analyze(sql, "sqlite", 32 * 1024).queryText)
    val sut = fixture.getSut(databaseName = "tracks.db")

    sut.performSql(sql) {}

    val span = fixture.sentryTracer.children.single()
    assertThat(span.description).isEqualTo(expected)
    assertThat(span.data).containsEntry(SpanDataConvention.DB_QUERY_TEXT_KEY, expected)
  }

  @Test
  fun `performSql keeps the raw query off the span when the analysis fails`() {
    val sql = "SELECT * FROM users WHERE email = 'Zq9xK7@x.io'"
    val sut = fixture.getSut(databaseName = "tracks.db")

    Mockito.mockStatic(OtelSqlSanitizer::class.java).use { sanitizer ->
      sanitizer
        .`when`<Any> { OtelSqlSanitizer.sanitize(any(), any()) }
        .thenThrow(IllegalStateException("lexer bug"))

      sut.performSql(sql) {}
    }

    val span = fixture.sentryTracer.children.single()
    assertThat(span.description).isNull()
    assertThat(span.data.keys)
      .containsNoneOf(
        SpanDataConvention.DB_QUERY_TEXT_KEY,
        SpanDataConvention.DB_QUERY_SUMMARY_KEY,
        SpanDataConvention.DB_STORED_PROCEDURE_NAME_KEY,
      )
    span.data.forEach { (key, value) ->
      assertWithMessage(key).that(value.toString()).doesNotContain("Zq9xK7")
    }
    assertThat(span.data).containsEntry(SpanDataConvention.DB_SYSTEM_KEY, "sqlite")
  }

  @Test
  fun `performSql does not analyze the query without an active parent span`() {
    listOf(null, NoOpSpan.getInstance()).forEach { parent ->
      listOf<() -> Unit>({}, { throw SQLException() }).forEach { operation ->
        val sut = fixture.getSut()
        whenever(fixture.scopes.span).thenReturn(parent)

        Mockito.mockStatic(OtelSqlSanitizer::class.java).use { sanitizer ->
          runCatching { sut.performSql("SELECT a FROM t", operation) }

          sanitizer.verifyNoInteractions()
        }
      }
    }
  }

  @Test
  fun `performSql does not create a span if no span is running`() {
    val sut = fixture.getSut(isSpanActive = false)
    sut.performSql("sql") {}
    assertEquals(0, fixture.sentryTracer.children.size)
  }

  @Test
  fun `performSql creates a span with error status if the operation throws`() {
    val sut = fixture.getSut()
    val e = SQLException()
    try {
      sut.performSql("error sql") { throw e }
    } catch (_: Throwable) {}
    val span = fixture.sentryTracer.children.firstOrNull()
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals("error sql", span.description)
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(e, span.throwable)
    assertTrue(span.isFinished)
  }

  @Test
  fun `when performSql runs in background blocked_main_thread is false and no stack trace is attached`() {
    val sut = fixture.getSut()

    fixture.options.threadChecker = mock<IThreadChecker>()
    whenever(fixture.options.threadChecker.isMainThread).thenReturn(false)
    whenever(fixture.options.threadChecker.currentThreadName).thenReturn("test")

    sut.performSql("sql") {}
    val span = fixture.sentryTracer.children.first()

    assertFalse(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
    assertNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
  }

  @Test
  fun `when performSql runs in foreground blocked_main_thread is true and a stack trace is attached`() {
    val sut = fixture.getSut()

    fixture.options.threadChecker = mock<IThreadChecker>()
    whenever(fixture.options.threadChecker.isMainThread).thenReturn(true)
    whenever(fixture.options.threadChecker.currentThreadName).thenReturn("test")

    sut.performSql("sql") {}
    val span = fixture.sentryTracer.children.first()

    assertTrue(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
    assertNotNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
  }

  @Test
  fun `when databaseName is provided, sets system and name as span data`() {
    val sut = fixture.getSut(databaseName = "tracks.db")

    sut.performSql("sql") {}
    val span = fixture.sentryTracer.children.first()

    assertEquals(span.data[SpanDataConvention.DB_SYSTEM_KEY], "sqlite")
    assertEquals(span.data[SpanDataConvention.DB_NAME_KEY], "tracks.db")
    assertEquals("sqlite", span.data[SpanDataConvention.DB_SYSTEM_NAME_KEY])
    assertEquals("tracks.db", span.data[SpanDataConvention.DB_NAMESPACE_KEY])
  }

  @Test
  fun `when databaseName is null, sets system to in-memory`() {
    val sut = fixture.getSut()

    sut.performSql("sql") {}
    val span = fixture.sentryTracer.children.first()

    assertEquals(span.data[SpanDataConvention.DB_SYSTEM_KEY], "in-memory")
    assertEquals("sqlite", span.data[SpanDataConvention.DB_SYSTEM_NAME_KEY])
    assertNull(span.data[SpanDataConvention.DB_NAMESPACE_KEY])
  }

  @Test
  fun `when performSql returns a CrossProcessCursor, does not start a span and returns a SentryCrossProcessCursor`() {
    val sut = fixture.getSut()

    // When performSql returns a CrossProcessCursor
    val result = sut.performSql("sql") { mock<CrossProcessCursor>() }

    // Returns a SentryCrossProcessCursor
    assertIs<SentryCrossProcessCursor>(result)
    // And no span is started
    assertNull(fixture.sentryTracer.children.firstOrNull())
  }

  private companion object {
    const val QUERY_NANOS = 5_000_000L
    const val LIMIT = 8 * 1024
  }
}
