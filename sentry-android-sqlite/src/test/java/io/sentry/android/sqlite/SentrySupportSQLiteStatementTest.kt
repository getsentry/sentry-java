package io.sentry.android.sqlite

import androidx.sqlite.db.SupportSQLiteStatement
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SentrySupportSQLiteStatementTest {

    private class Fixture {
        private val scopes = mock<IScopes>()
        private val spanManager = SQLiteSpanManager(scopes)
        val mockStatement = mock<SupportSQLiteStatement>()
        lateinit var sentryTracer: SentryTracer
        lateinit var options: SentryOptions

        fun getSut(sql: String, isSpanActive: Boolean = true): SentrySupportSQLiteStatement {
            options = SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
            }
            whenever(scopes.options).thenReturn(options)
            sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)

            if (isSpanActive) {
                whenever(scopes.span).thenReturn(sentryTracer)
            }
            return SentrySupportSQLiteStatement(mockStatement, spanManager, sql)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `all calls are propagated to the delegate`() {
        val sql = "sql"
        val statement = fixture.getSut(sql)

        inOrder(fixture.mockStatement) {
            statement.execute()
            verify(fixture.mockStatement).execute()

            statement.executeUpdateDelete()
            verify(fixture.mockStatement).executeUpdateDelete()

            statement.executeInsert()
            verify(fixture.mockStatement).executeInsert()

            statement.simpleQueryForLong()
            verify(fixture.mockStatement).simpleQueryForLong()

            statement.simpleQueryForString()
            verify(fixture.mockStatement).simpleQueryForString()
        }
    }

    @Test
    fun `execute creates a span if a span is running`() {
        val sql = "execute"
        val sut = fixture.getSut(sql)
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.execute()
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `execute does not create a span if no span is running`() {
        val sut = fixture.getSut("execute", isSpanActive = false)
        sut.execute()
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `executeUpdateDelete creates a span if a span is running`() {
        val sql = "executeUpdateDelete"
        val sut = fixture.getSut(sql)
        whenever(fixture.mockStatement.executeUpdateDelete()).thenReturn(10)
        assertEquals(0, fixture.sentryTracer.children.size)
        val result = sut.executeUpdateDelete()
        assertEquals(10, result)
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `executeUpdateDelete does not create a span if no span is running`() {
        val sut = fixture.getSut("executeUpdateDelete", isSpanActive = false)
        whenever(fixture.mockStatement.executeUpdateDelete()).thenReturn(10)
        val result = sut.executeUpdateDelete()
        assertEquals(10, result)
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `executeInsert creates a span if a span is running`() {
        val sql = "executeInsert"
        val sut = fixture.getSut(sql)
        whenever(fixture.mockStatement.executeInsert()).thenReturn(10)
        assertEquals(0, fixture.sentryTracer.children.size)
        val result = sut.executeInsert()
        assertEquals(10, result)
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `executeInsert does not create a span if no span is running`() {
        val sut = fixture.getSut("executeInsert", isSpanActive = false)
        whenever(fixture.mockStatement.executeInsert()).thenReturn(10)
        val result = sut.executeInsert()
        assertEquals(10, result)
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `simpleQueryForLong creates a span if a span is running`() {
        val sql = "simpleQueryForLong"
        val sut = fixture.getSut(sql)
        whenever(fixture.mockStatement.simpleQueryForLong()).thenReturn(10)
        assertEquals(0, fixture.sentryTracer.children.size)
        val result = sut.simpleQueryForLong()
        assertEquals(10, result)
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `simpleQueryForLong does not create a span if no span is running`() {
        val sut = fixture.getSut("simpleQueryForLong", isSpanActive = false)
        whenever(fixture.mockStatement.simpleQueryForLong()).thenReturn(10)
        val result = sut.simpleQueryForLong()
        assertEquals(10, result)
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `simpleQueryForString creates a span if a span is running`() {
        val sql = "simpleQueryForString"
        val sut = fixture.getSut(sql)
        whenever(fixture.mockStatement.simpleQueryForString()).thenReturn("10")
        assertEquals(0, fixture.sentryTracer.children.size)
        val result = sut.simpleQueryForString()
        assertEquals("10", result)
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `simpleQueryForString does not create a span if no span is running`() {
        val sut = fixture.getSut("simpleQueryForString", isSpanActive = false)
        whenever(fixture.mockStatement.simpleQueryForString()).thenReturn("10")
        val result = sut.simpleQueryForString()
        assertEquals("10", result)
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    private fun assertSqlSpanCreated(sql: String, span: ISpan?) {
        assertNotNull(span)
        assertEquals("db.sql.query", span.operation)
        assertEquals(sql, span.description)
        assertEquals(SpanStatus.OK, span.status)
        assertTrue(span.isFinished)
    }
}
