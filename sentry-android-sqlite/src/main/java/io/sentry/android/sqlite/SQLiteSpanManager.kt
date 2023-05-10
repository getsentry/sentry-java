package io.sentry.android.sqlite

import android.database.SQLException
import android.database.sqlite.SQLiteDoneException
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.SpanStatus

class SQLiteSpanManager(
    private val hub: IHub = HubAdapter.getInstance()
) {
    private val options: SentryOptions = hub.options

    private val transactionSpans: MutableList<ISpan> = ArrayList()

    init {
        SentryIntegrationPackageStorage.getInstance().addIntegration("SQLite")
    }

    /**
     * Performs a sql operation, creates a span and handles exceptions in case of occurrence.
     *
     * @param op A sql operation to execute (e.g. execute or execPerConnectionSQL)
     * @param sql The sql query
     * @param operation The sql operation to execute. In case of an error the surrounding span will have its status set to INTERNAL_ERROR
     */
    @Throws(SQLException::class)
    fun <T> performSql(op: String, sql: String, operation: () -> T): T {
        // If a transaction is running we create spans as children of that, otherwise we just create a span for the current transaction
        val parentSpan = transactionSpans.lastOrNull() ?: Sentry.getSpan()
        val span = parentSpan?.startChild("SQLite", op)
        span?.setData("query", sql)
        return try {
            val result = operation()
            result
        } catch (e: Throwable) {
            span?.status = SpanStatus.INTERNAL_ERROR
            span?.throwable = e
            throw e
        } finally {
            span?.finish()
        }
    }

    /**
     * Creates a span and adds it to the list of current transactions. When a transaction is ended, it gets removed from the list.
     * Multiple transactions can be started at the same time, nested into each other.
     */
    fun beginTransaction() {
        val parentSpan = transactionSpans.lastOrNull() ?: Sentry.getSpan()
        val span = parentSpan?.startChild("SQLite", "transaction") ?: return
        // The span status is set to INTERNAL_ERROR and will be set to OK when transaction.setSuccessful will be called
        span.status = SpanStatus.INTERNAL_ERROR
        transactionSpans.add(span)
    }

    /** Removes the last span from the list of current transactions. */
    fun endTransaction() {
        transactionSpans.removeLastOrNull()?.finish()
    }

    /** Sets the status of the last span of current transactions to OK. */
    fun setTransactionSuccessful() {
        transactionSpans.lastOrNull()?.status = SpanStatus.OK
    }

}
