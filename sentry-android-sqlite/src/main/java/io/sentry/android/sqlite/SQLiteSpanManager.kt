package io.sentry.android.sqlite

import android.database.SQLException
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryStackTraceFactory
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus

private const val TRACE_ORIGIN = "auto.db.sqlite"

internal class SQLiteSpanManager(
    private val hub: IHub = HubAdapter.getInstance(),
    private val databaseName: String? = null
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
        span?.spanContext?.origin = TRACE_ORIGIN
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
                // if db name is null, then it's an in-memory database as per
                // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:sqlite/sqlite/src/main/java/androidx/sqlite/db/SupportSQLiteOpenHelper.kt;l=38-42
                if (databaseName != null) {
                    setData(SpanDataConvention.DB_SYSTEM_KEY, "sqlite")
                    setData(SpanDataConvention.DB_NAME_KEY, databaseName)
                } else {
                    setData(SpanDataConvention.DB_SYSTEM_KEY, "in-memory")
                }

                finish()
            }
        }
    }
}
