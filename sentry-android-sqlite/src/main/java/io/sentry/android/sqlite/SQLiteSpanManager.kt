package io.sentry.android.sqlite

import android.database.SQLException
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.Sentry
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.SpanStatus

class SQLiteSpanManager(
    private val hub: IHub = HubAdapter.getInstance()
) {
    private val options: SentryOptions = hub.options

    init {
        SentryIntegrationPackageStorage.getInstance().addIntegration("SQLite")
    }

    /**
     * Performs file IO, counts the read/written bytes and handles exceptions in case of occurence
     *
     * @param operation An IO operation to execute (e.g. [FileInputStream.read] or [     ][FileOutputStream.write] The operation is of a type [Integer] or [Long],
     * where the output is the result of the IO operation (byte count read/written)
     */
    @Throws(SQLException::class)
    fun <T> performSql(operation: () -> T): T {
        val span = Sentry.getSpan()?.startChild("executeUpdateDelete")
        return try {
            val result = operation()
            result
        } catch (exception: SQLException) {
            span?.status = SpanStatus.INTERNAL_ERROR
            span?.throwable = exception
            throw exception
        } finally {
            span?.finish()
        }
    }

}
