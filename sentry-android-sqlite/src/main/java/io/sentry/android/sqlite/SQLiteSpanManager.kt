package io.sentry.android.sqlite

import android.database.CrossProcessCursor
import android.database.SQLException
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SpanStatus
import io.sentry.sqlite.SQLiteSpanInstrumentation

internal class SQLiteSpanManager(
  private val scopes: IScopes = ScopesAdapter.getInstance(),
  databaseName: String? = null,
) {

  private val spans = SQLiteSpanInstrumentation.fromDatabaseName(databaseName, scopes)

  init {
    SentryIntegrationPackageStorage.getInstance().addIntegration("SQLite")
  }

  /**
   * Performs a sql operation, creates a span and handles exceptions in case of occurrence.
   *
   * @param sql The sql query
   * @param operation The sql operation to execute. In case of an error the surrounding span will
   *   have its status set to INTERNAL_ERROR
   */
  @Suppress("TooGenericExceptionCaught", "UNCHECKED_CAST")
  @Throws(SQLException::class)
  fun <T> performSql(sql: String, operation: () -> T): T {
    val startTimestamp = spans.startTimestamp()

    return try {
      val result = operation()
      /*
       * SQLiteCursor - that extends CrossProcessCursor - executes the query lazily, when one of
       *  getCount() or onMove() is called. In this case we don't have to start the span here.
       * Otherwise we start the span with the timestamp taken before the operation started.
       */
      if (result is CrossProcessCursor) {
        return SentryCrossProcessCursor(result, this, sql) as T
      }
      spans.recordSpan(sql, startTimestamp, SpanStatus.OK)
      result
    } catch (e: Throwable) {
      spans.recordSpan(sql, startTimestamp, SpanStatus.INTERNAL_ERROR, e)
      throw e
    }
  }
}
