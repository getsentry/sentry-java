package io.sentry.android.sqlite

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SentrySupportSQLiteDatabaseTest {
    private class Fixture {
        private val scopes = mock<IScopes>()
        private val spanManager = SQLiteSpanManager(scopes)
        val mockDatabase = mock<SupportSQLiteDatabase>()
        lateinit var sentryTracer: SentryTracer
        lateinit var options: SentryOptions

        init {
            whenever(mockDatabase.compileStatement(any())).thenReturn(mock())
        }

        fun getSut(isSpanActive: Boolean = true): SentrySupportSQLiteDatabase {
            options =
                SentryOptions().apply {
                    dsn = "https://key@sentry.io/proj"
                }
            whenever(scopes.options).thenReturn(options)
            sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)

            if (isSpanActive) {
                whenever(scopes.span).thenReturn(sentryTracer)
            }

            return SentrySupportSQLiteDatabase(mockDatabase, spanManager)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `all calls are propagated to the delegate`() {
        val sql = "sql"
        val dummySqLiteQuery = mock<SupportSQLiteQuery>()
        whenever(dummySqLiteQuery.sql).thenReturn(sql)
        val sut = fixture.getSut()

        inOrder(fixture.mockDatabase) {
            sut.compileStatement(sql)
            verify(fixture.mockDatabase).compileStatement(eq(sql))

            sut.execPerConnectionSQL(sql, emptyArray())
            verify(fixture.mockDatabase).execPerConnectionSQL(eq(sql), any())

            var res = sut.query(sql)
            verify(fixture.mockDatabase).query(eq(sql))
            assertIs<Cursor?>(res)

            res = sut.query(sql, emptyArray())
            verify(fixture.mockDatabase).query(eq(sql), any())
            assertIs<Cursor?>(res)

            res = sut.query(dummySqLiteQuery)
            verify(fixture.mockDatabase).query(eq(dummySqLiteQuery))
            assertIs<Cursor?>(res)

            res = sut.query(dummySqLiteQuery, mock())
            verify(fixture.mockDatabase).query(eq(dummySqLiteQuery), any())
            assertIs<Cursor?>(res)

            sut.execSQL(sql)
            verify(fixture.mockDatabase).execSQL(eq(sql))

            sut.execSQL(sql, emptyArray())
            verify(fixture.mockDatabase).execSQL(eq(sql), any())

            sut.execPerConnectionSQL(sql, emptyArray())
            verify(fixture.mockDatabase).execPerConnectionSQL(eq(sql), any())
        }
    }

    @Test
    fun `compileStatement returns a SentrySupportSQLiteStatement`() {
        val sut = fixture.getSut()
        val compiled = sut.compileStatement("sql")
        assertNotNull(compiled)
        assertIs<SentrySupportSQLiteStatement>(compiled)
    }

    @Test
    fun `execPerConnectionSQL creates a span if a span is running`() {
        val sql = "execPerConnectionSQL"
        val sut = fixture.getSut()
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.execPerConnectionSQL(sql, emptyArray())
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `execPerConnectionSQL does not create a span if no span is running`() {
        val sut = fixture.getSut(isSpanActive = false)
        sut.execPerConnectionSQL("execPerConnectionSQL", emptyArray())
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `query creates a span if a span is running`() {
        val sql = "query"
        val sut = fixture.getSut()
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.query(sql)
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `query does not create a span if no span is running`() {
        val sut = fixture.getSut(isSpanActive = false)
        sut.query("query")
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `query with bindArgs creates a span if a span is running`() {
        val sql = "query"
        val sut = fixture.getSut()
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.query(sql, emptyArray())
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `query with bindArgs does not create a span if no span is running`() {
        val sut = fixture.getSut(isSpanActive = false)
        sut.query("query", emptyArray())
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `query with SupportSQLiteQuery creates a span if a span is running`() {
        val sql = "query"
        val sut = fixture.getSut()
        val query = mock<SupportSQLiteQuery>()
        whenever(query.sql).thenReturn(sql)
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.query(query)
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `query with SupportSQLiteQuery does not create a span if no span is running`() {
        val sut = fixture.getSut(isSpanActive = false)
        val query = mock<SupportSQLiteQuery>()
        whenever(query.sql).thenReturn("query")
        sut.query(query)
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `query with SupportSQLiteQuery and CancellationSignal creates a span if a span is running`() {
        val sql = "query"
        val sut = fixture.getSut()
        val query = mock<SupportSQLiteQuery>()
        whenever(query.sql).thenReturn(sql)
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.query(query, mock())
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `query with SupportSQLiteQuery and CancellationSignal does not create a span if no span is running`() {
        val sut = fixture.getSut(isSpanActive = false)
        val query = mock<SupportSQLiteQuery>()
        whenever(query.sql).thenReturn("query")
        sut.query(query, mock())
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `execSQL creates a span if a span is running`() {
        val sql = "execSQL"
        val sut = fixture.getSut()
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.execSQL(sql)
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `execSQL does not create a span if no span is running`() {
        val sut = fixture.getSut(isSpanActive = false)
        sut.execSQL("execSQL")
        assertEquals(0, fixture.sentryTracer.children.size)
    }

    @Test
    fun `execSQL with bindArgs creates a span if a span is running`() {
        val sql = "execSQL"
        val sut = fixture.getSut()
        assertEquals(0, fixture.sentryTracer.children.size)
        sut.execSQL(sql, emptyArray())
        val span = fixture.sentryTracer.children.firstOrNull()
        assertSqlSpanCreated(sql, span)
    }

    @Test
    fun `execSQL with bindArgs does not create a span if no span is running`() {
        val sut = fixture.getSut(isSpanActive = false)
        sut.execSQL("execSQL", emptyArray())
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
