package io.sentry.android.sqlite

import android.database.CrossProcessCursor
import android.database.SQLException
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ScopesAdapter
import io.sentry.SentryDate
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryStackTraceFactory
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.sqlite.DB_SYSTEM_IN_MEMORY
import io.sentry.sqlite.DB_SYSTEM_SQLITE
import io.sentry.sqlite.MAX_QUERY_TEXT_LENGTH
import io.sentry.util.sql.SqlSpanData

private const val TRACE_ORIGIN = "auto.db.sqlite"

/** Span instrumentation for [SentrySupportSQLiteOpenHelper]. */
internal class OpenHelperSpans(
  private val scopes: IScopes = ScopesAdapter.getInstance(),
  private val databaseName: String? = null,
) {
  private val stackTraceFactory = SentryStackTraceFactory(scopes.options)

  // if db name is null, then it's an in-memory database as per
  // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:sqlite/sqlite/src/main/java/androidx/sqlite/db/SupportSQLiteOpenHelper.kt;l=38-42
  private val dbSystem = if (databaseName != null) DB_SYSTEM_SQLITE else DB_SYSTEM_IN_MEMORY

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
      span = startSpan(sql, startTimestamp)
      span?.spanContext?.origin = TRACE_ORIGIN
      span?.status = SpanStatus.OK
      result
    } catch (e: Throwable) {
      span = startSpan(sql, startTimestamp)
      span?.spanContext?.origin = TRACE_ORIGIN
      span?.status = SpanStatus.INTERNAL_ERROR
      span?.throwable = e
      throw e
    } finally {
      span?.apply {
        val isMainThread: Boolean = scopes.options.threadChecker.isMainThread
        setData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY, isMainThread)
        if (isMainThread) {
          setData(SpanDataConvention.CALL_STACK_KEY, stackTraceFactory.inAppCallStack)
        }
        setData(SpanDataConvention.DB_SYSTEM_KEY, dbSystem)
        SqlSpanData.toDbSystemName(dbSystem)?.let {
          setData(SpanDataConvention.DB_SYSTEM_NAME_KEY, it)
        }
        if (databaseName != null) {
          setData(SpanDataConvention.DB_NAME_KEY, databaseName)
          setData(SpanDataConvention.DB_NAMESPACE_KEY, databaseName)
        }

        finish()
      }
    }
  }

  /**
   * Starts the `db.sql.query` span with the sanitized [sql], or returns `null` without analyzing
   * [sql] if there is no active parent span.
   */
  private fun startSpan(sql: String, startTimestamp: SentryDate): ISpan? {
    val parent = scopes.span
    if (parent == null || parent.isNoOp) {
      return null
    }
    val queryData = SqlSpanData.analyze(sql, dbSystem, MAX_QUERY_TEXT_LENGTH, scopes.options.logger)
    return parent.startChild("db.sql.query", queryData.description, startTimestamp).also {
      queryData.applyTo(it)
    }
  }
}
