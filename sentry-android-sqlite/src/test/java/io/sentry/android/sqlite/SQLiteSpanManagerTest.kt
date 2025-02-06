package io.sentry.android.sqlite

import android.database.CrossProcessCursor
import android.database.SQLException
import io.sentry.IScopes
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.util.thread.IThreadChecker
import org.junit.Before
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SQLiteSpanManagerTest {

    private class Fixture {
        private val scopes = mock<IScopes>()
        lateinit var sentryTracer: SentryTracer
        lateinit var options: SentryOptions

        fun getSut(isSpanActive: Boolean = true, databaseName: String? = null): SQLiteSpanManager {
            options = SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
            }
            whenever(scopes.options).thenReturn(options)
            sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)

            if (isSpanActive) {
                whenever(scopes.span).thenReturn(sentryTracer)
            }
            return SQLiteSpanManager(scopes, databaseName)
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
            sut.performSql("error sql") {
                throw e
            }
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
    }

    @Test
    fun `when databaseName is null, sets system to in-memory`() {
        val sut = fixture.getSut()

        sut.performSql("sql") {}
        val span = fixture.sentryTracer.children.first()

        assertEquals(span.data[SpanDataConvention.DB_SYSTEM_KEY], "in-memory")
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
