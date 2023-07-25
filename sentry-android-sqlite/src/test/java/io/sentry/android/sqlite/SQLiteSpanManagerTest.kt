package io.sentry.android.sqlite

import android.database.SQLException
import io.sentry.IHub
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.util.thread.IMainThreadChecker
import org.junit.Before
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SQLiteSpanManagerTest {

    private class Fixture {
        private val hub = mock<IHub>()
        lateinit var sentryTracer: SentryTracer
        lateinit var options: SentryOptions

        fun getSut(isSpanActive: Boolean = true): SQLiteSpanManager {
            options = SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
            }
            whenever(hub.options).thenReturn(options)
            sentryTracer = SentryTracer(TransactionContext("name", "op"), hub)

            if (isSpanActive) {
                whenever(hub.span).thenReturn(sentryTracer)
            }
            return SQLiteSpanManager(hub)
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

        fixture.options.mainThreadChecker = mock<IMainThreadChecker>()
        whenever(fixture.options.mainThreadChecker.isMainThread).thenReturn(false)

        sut.performSql("sql") {}
        val span = fixture.sentryTracer.children.first()

        assertFalse(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
        assertNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
    }

    @Test
    fun `when performSql runs in foreground blocked_main_thread is true and a stack trace is attached`() {
        val sut = fixture.getSut()

        fixture.options.mainThreadChecker = mock<IMainThreadChecker>()
        whenever(fixture.options.mainThreadChecker.isMainThread).thenReturn(true)

        sut.performSql("sql") {}
        val span = fixture.sentryTracer.children.first()

        assertTrue(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
        assertNotNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
    }
}
