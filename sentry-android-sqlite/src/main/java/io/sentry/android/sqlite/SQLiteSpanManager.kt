package io.sentry.android.sqlite

import android.database.SQLException
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryStackTraceFactory
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus

internal class SQLiteSpanManager(
    private val hub: IHub = HubAdapter.getInstance()
) {
    private val stackTraceFactory = SentryStackTraceFactory(hub.options)

    init {
        SentryIntegrationPackageStorage.getInstance().addIntegration("SQLite")
    }

    /**
     * Performs a sql operation, creates a span and handles exceptions in case of occurrence.
     *
     * @param sql The sql query
     * @param operation The sql operation to execute.
     *  In case of an error the surrounding span will have its status set to INTERNAL_ERROR
     */
    @Suppress("TooGenericExceptionCaught")
    @Throws(SQLException::class)
    fun <T> performSql(sql: String, operation: () -> T): T {
        val span = hub.span?.startChild("db.sql.query", sql)
        span?.spanContext?.origin = "auto.db.sqlite"
        return try {
            val result = operation()
            span?.status = SpanStatus.OK
            result
        } catch (e: Throwable) {
            span?.status = SpanStatus.INTERNAL_ERROR
            span?.throwable = e
            throw e
        } finally {
            span?.apply {
                val isMainThread: Boolean = hub.options.mainThreadChecker.isMainThread
                setData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY, isMainThread)
                if (isMainThread) {
                    setData(SpanDataConvention.CALL_STACK_KEY, stackTraceFactory.inAppCallStack)
                }
                finish()
            }
        }
    }
}
