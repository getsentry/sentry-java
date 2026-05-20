package io.sentry.android.sqlite

import android.database.CrossProcessCursor
import android.database.SQLException
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ScopesAdapter
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SpanStatus
import io.sentry.sqlite.SQLiteSpanHelper
import io.sentry.sqlite.dbMetadataFromDatabaseName

internal class SQLiteSpanManager(
  private val scopes: IScopes = ScopesAdapter.getInstance(),
  databaseName: String? = null,
) {

  private val spanHelper = SQLiteSpanHelper(scopes, dbMetadataFromDatabaseName(databaseName))

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
    val startTimestamp = scopes.getOptions().dateProvider.now()
    var span: ISpan? = null
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
      span = spanHelper.startSpan(sql, startTimestamp)
      span?.status = SpanStatus.OK
      result
    } catch (e: Throwable) {
      span = spanHelper.startSpan(sql, startTimestamp)
      span?.status = SpanStatus.INTERNAL_ERROR
      span?.throwable = e
      throw e
    } finally {
      span?.let {
        spanHelper.applyDataToSpan(it)
        it.finish()
      }
    }
  }
}
