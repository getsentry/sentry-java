package io.sentry.android.sqlite

import androidx.sqlite.SQLiteStatement
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentrySQLiteStatementTest {
  private class Fixture {
    private val scopes = mock<IScopes>()
    private val spanManager = SQLiteSpanManager(scopes)
    val mockStatement = mock<SQLiteStatement>()
    lateinit var sentryTracer: SentryTracer
    lateinit var options: SentryOptions

    fun getSut(sql: String, isSpanActive: Boolean = true): SentrySQLiteStatement {
      options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
      whenever(scopes.options).thenReturn(options)
      sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)

      if (isSpanActive) {
        whenever(scopes.span).thenReturn(sentryTracer)
      }
      return SentrySQLiteStatement(mockStatement, spanManager, sql)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `all calls are propagated to the delegate`() {
    val sql = "SELECT * FROM users"
    val statement = fixture.getSut(sql)

    whenever(fixture.mockStatement.step()).thenReturn(true)

    inOrder(fixture.mockStatement) {
      statement.step()
      verify(fixture.mockStatement).step()

      statement.reset()
      verify(fixture.mockStatement).reset()
    }
  }

  @Test
  fun `step creates a span if a span is running`() {
    val sql = "SELECT * FROM users"
    val sut = fixture.getSut(sql)
    whenever(fixture.mockStatement.step()).thenReturn(true)
    assertEquals(0, fixture.sentryTracer.children.size)
    sut.step()
    val span = fixture.sentryTracer.children.firstOrNull()
    assertSqlSpanCreated(sql, span)
  }

  @Test
  fun `step does not create a span if no span is running`() {
    val sql = "SELECT * FROM users"
    val sut = fixture.getSut(sql, isSpanActive = false)
    whenever(fixture.mockStatement.step()).thenReturn(true)
    sut.step()
    assertEquals(0, fixture.sentryTracer.children.size)
  }

  @Test
  fun `multiple step calls only create one span`() {
    val sql = "SELECT * FROM users"
    val sut = fixture.getSut(sql)
    whenever(fixture.mockStatement.step()).thenReturn(true, true, false)
    assertEquals(0, fixture.sentryTracer.children.size)

    // First step creates a span
    sut.step()
    assertEquals(1, fixture.sentryTracer.children.size)

    // Second step doesn't create a new span
    sut.step()
    assertEquals(1, fixture.sentryTracer.children.size)

    // Third step still doesn't create a new span
    sut.step()
    assertEquals(1, fixture.sentryTracer.children.size)

    val span = fixture.sentryTracer.children.firstOrNull()
    assertSqlSpanCreated(sql, span)
  }

  @Test
  fun `reset allows step to create a new span`() {
    val sql = "SELECT * FROM users"
    val sut = fixture.getSut(sql)
    whenever(fixture.mockStatement.step()).thenReturn(true)
    assertEquals(0, fixture.sentryTracer.children.size)

    // First step creates a span
    sut.step()
    assertEquals(1, fixture.sentryTracer.children.size)

    // Reset the statement
    sut.reset()

    // Next step creates a new span
    sut.step()
    assertEquals(2, fixture.sentryTracer.children.size)

    // Verify both spans were created correctly
    fixture.sentryTracer.children.forEach { span ->
      assertSqlSpanCreated(sql, span)
    }
  }

  @Test
  fun `step returns delegate result`() {
    val sql = "SELECT * FROM users"
    val sut = fixture.getSut(sql)
    whenever(fixture.mockStatement.step()).thenReturn(true, false)

    val result1 = sut.step()
    assertTrue(result1)

    sut.reset()

    val result2 = sut.step()
    assertFalse(result2)
  }

  private fun assertSqlSpanCreated(sql: String, span: ISpan?) {
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals(sql, span.description)
    assertEquals(SpanStatus.OK, span.status)
    assertTrue(span.isFinished)
  }
}
