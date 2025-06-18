package io.sentry.android.sqlite

import android.database.CrossProcessCursor
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SentryCrossProcessCursorTest {
    private class Fixture {
        private val scopes = mock<IScopes>()
        private val spanManager = SQLiteSpanManager(scopes)
        val mockCursor = mock<CrossProcessCursor>()
        lateinit var options: SentryOptions
        lateinit var sentryTracer: SentryTracer

        fun getSut(
            sql: String,
            isSpanActive: Boolean = true,
        ): SentryCrossProcessCursor {
            options =
                SentryOptions().apply {
                    dsn = "https://key@sentry.io/proj"
                }
            whenever(scopes.options).thenReturn(options)
            sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)

            if (isSpanActive) {
                whenever(scopes.span).thenReturn(sentryTracer)
            }
            return SentryCrossProcessCursor(mockCursor, spanManager, sql)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `all calls are propagated to the delegate`() {
        val sql = "sql"
        val cursor = fixture.getSut(sql)

        cursor.onMove(0, 1)
        verify(fixture.mockCursor).onMove(eq(0), eq(1))

        cursor.count
        verify(fixture.mockCursor).count

        cursor.fillWindow(0, mock())
        verify(fixture.mockCursor).fillWindow(eq(0), any())

        // Let's verify other methods are delegated, even if not explicitly
        cursor.close()
        verify(fixture.mockCursor).close()

        cursor.getString(1)
        verify(fixture.mockCursor).getString(eq(1))
    }

    @Test
    fun `getCount creates a span if a span is running`() {
        val sql = "execute"
        val sut = fixture.getSut(sql)
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.count
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `getCount does not create a span if no span is running`() {
        val sut = fixture.getSut("execute", isSpanActive = false)
        sut.count
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `onMove creates a span if a span is running`() {
        val sql = "execute"
        val sut = fixture.getSut(sql)
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.onMove(0, 5)
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `onMove does not create a span if no span is running`() {
        val sut = fixture.getSut("execute", isSpanActive = false)
        sut.onMove(0, 5)
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `fillWindow creates a span if a span is running`() {
        val sql = "execute"
        val sut = fixture.getSut(sql)
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.fillWindow(0, mock())
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `fillWindow does not create a span if no span is running`() {
        val sut = fixture.getSut("execute", isSpanActive = false)
        sut.fillWindow(0, mock())
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    private fun assertSqlSpanCreated(
        sql: String,
        span: ISpan?,
    ) {
        assertNotNull(span)
        assertEquals("db.sql.query", span.operation)
        assertEquals(sql, span.description)
        assertEquals(SpanStatus.OK, span.status)
        assertTrue(span.isFinished)
    }
}
