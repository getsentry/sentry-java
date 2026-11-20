package io.sentry.android.sqlite

import android.database.CrossProcessCursor
import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.sentry.IScopes
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.sqlite.assertNoCanary
import io.sentry.util.thread.IThreadChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenHelperSpansTest {
  private class Fixture {
    private val scopes = mock<IScopes>()
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
    sut.performSql("SELECT a FROM t") {}
    val span = fixture.sentryTracer.children.firstOrNull()
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals("auto.db.sqlite", span.spanContext.origin)
    assertEquals("SELECT a FROM t", span.description)
    assertEquals(SpanStatus.OK, span.status)
    assertTrue(span.isFinished)
  }

  @Test
  fun `performSql keeps literals and comments out of every span field`() {
    // input to (description and db.query.text, db.query.summary)
    val cases =
      listOf(
        Triple("SELECT a FROM t WHERE p = 'canary'", "SELECT a FROM t WHERE p = ?", "SELECT t"),
        Triple("SELECT a /* canary */ FROM t", "SELECT a FROM t", "SELECT t"),
        Triple("SELECT a FROM t -- canary\nWHERE b = 1", "SELECT a FROM t WHERE b = ?", "SELECT t"),
        // STOPPED: unterminated literal
        Triple("UPDATE t SET p = 'canary", "UPDATE t SET p = ?", "UPDATE t"),
      )
    val sut = fixture.getSut(databaseName = "tracks.db")

    cases.forEach { (sql, _, _) -> sut.performSql(sql) {} }

    assertThat(fixture.sentryTracer.children).hasSize(cases.size)
    cases.zip(fixture.sentryTracer.children).forEach { (case, span) ->
      val (sql, text, summary) = case
      assertWithMessage(sql).that(span.description).isEqualTo(text)
      assertWithMessage(sql).that(span.data[SpanDataConvention.DB_QUERY_TEXT_KEY]).isEqualTo(text)
      assertWithMessage(sql)
        .that(span.data[SpanDataConvention.DB_QUERY_SUMMARY_KEY])
        .isEqualTo(summary)
      assertNoCanary(span)
    }
  }

  @Test
  fun `performSql reports the truncated sanitized text for a query over the limit`() {
    val sql =
      "SELECT a FROM orders WHERE note = 'canary' OR " +
        (0 until 1000).joinToString(" OR ") { "c$it IS NULL" }
    val sut = fixture.getSut(databaseName = "tracks.db")

    sut.performSql(sql) {}

    val span = fixture.sentryTracer.children.single()
    assertThat(span.description).startsWith("SELECT a FROM orders WHERE note = ? OR c0 IS NULL OR ")
    assertThat(span.description).hasLength(8192)
    assertThat(span.data[SpanDataConvention.DB_QUERY_TEXT_KEY]).isEqualTo(span.description)
    assertThat(span.data[SpanDataConvention.DB_QUERY_SUMMARY_KEY]).isEqualTo("SELECT orders")
    assertNoCanary(span)
  }

  @Test
  fun `performSql sanitizes queries on an in-memory database with SQLite rules`() {
    // SQLite has no backslash escapes; with unknown rules the analyzer stops after 'x\'
    val sut = fixture.getSut(databaseName = null)

    sut.performSql("SELECT a FROM t WHERE b = 'x\\' AND c = 'canary'") {}

    val span = fixture.sentryTracer.children.single()
    assertThat(span.description).isEqualTo("SELECT a FROM t WHERE b = ? AND c = ?")
    assertNoCanary(span)
  }

  @Test
  fun `performSql does not create a span if no span is running`() {
    val sut = fixture.getSut(isSpanActive = false)
    sut.performSql("sql") {}
    assertEquals(0, fixture.sentryTracer.children.size)
  }

  @Test
  fun `performSql creates a span with sanitized query and error status if the operation throws`() {
    val sut = fixture.getSut()
    val e = SQLException()
    try {
      sut.performSql("DELETE FROM t WHERE p = 'canary'") { throw e }
    } catch (_: Throwable) {}
    val span = fixture.sentryTracer.children.firstOrNull()
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals("DELETE FROM t WHERE p = ?", span.description)
    assertEquals("DELETE FROM t WHERE p = ?", span.data[SpanDataConvention.DB_QUERY_TEXT_KEY])
    assertEquals("DELETE t", span.data[SpanDataConvention.DB_QUERY_SUMMARY_KEY])
    assertNoCanary(span)
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
}
