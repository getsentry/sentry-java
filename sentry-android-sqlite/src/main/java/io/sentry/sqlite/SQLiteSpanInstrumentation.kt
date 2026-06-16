package io.sentry.sqlite

import io.sentry.IScopes
import io.sentry.Instrumenter
import io.sentry.ScopesAdapter
import io.sentry.SentryDate
import io.sentry.SentryLongDate
import io.sentry.SentryStackTraceFactory
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus

private const val SQLITE_TRACE_ORIGIN = "auto.db.sqlite"

/** Shared span instrumentation for SQLite. */
internal class SQLiteSpanInstrumentation(
  private val scopes: IScopes,
  private val dbMetadata: DbMetadata,
) {

  private val stackTraceFactory = SentryStackTraceFactory(scopes.options)

  /**
   * Returns a start timestamp for a `db.sql.query` span.
   *
   * Exposed so callers can capture a wall-clock start before accumulating database time.
   * Internalizing the start time in [recordSpan] would shift spans to end-of-work on the trace
   * timeline, which is less desirable.
   */
  fun startTimestamp(): SentryDate = scopes.options.dateProvider.now()

  /** Records a `db.sql.query` span from [startTimestamp] to the moment of invocation. */
  fun recordSpan(
    sql: String,
    startTimestamp: SentryDate,
    status: SpanStatus,
    throwable: Throwable? = null,
  ) {
    recordSpan(sql, startTimestamp, endTimestamp = null, status, throwable)
  }

  /** Records a `db.sql.query` span from [startTimestamp] to [startTimestamp] + [durationNanos]. */
  fun recordSpan(
    sql: String,
    startTimestamp: SentryDate,
    durationNanos: Long,
    status: SpanStatus,
    throwable: Throwable? = null,
  ) {
    val endTimestamp = SentryLongDate(startTimestamp.nanoTimestamp() + durationNanos)
    recordSpan(sql, startTimestamp, endTimestamp, status, throwable)
  }

  private fun recordSpan(
    sql: String,
    startTimestamp: SentryDate,
    endTimestamp: SentryDate?,
    status: SpanStatus,
    throwable: Throwable?,
  ) {
    scopes.span?.startChild("db.sql.query", sql, startTimestamp, Instrumenter.SENTRY)?.apply {
      spanContext.origin = SQLITE_TRACE_ORIGIN
      throwable?.let { this.throwable = it }

      val isMainThread = scopes.options.threadChecker.isMainThread
      setData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY, isMainThread)

      if (isMainThread) {
        setData(SpanDataConvention.CALL_STACK_KEY, stackTraceFactory.inAppCallStack)
      }

      dbMetadata.name?.let { setData(SpanDataConvention.DB_NAME_KEY, it) }
      setData(SpanDataConvention.DB_SYSTEM_KEY, dbMetadata.system)
      finish(status, endTimestamp)
    }
  }

  companion object {

    /**
     * Returns [SQLiteSpanInstrumentation] based on the [fileName] argument passed to
     * [SQLiteDriver.open][androidx.sqlite.SQLiteDriver.open].
     */
    fun fromFileName(
      fileName: String,
      scopes: IScopes = ScopesAdapter.getInstance(),
    ): SQLiteSpanInstrumentation =
      SQLiteSpanInstrumentation(scopes, dbMetadataFromFileName(fileName))

    /**
     * Returns [SQLiteSpanInstrumentation] based on
     * [SupportSQLiteOpenHelper.databaseName][androidx.sqlite.db.SupportSQLiteOpenHelper.databaseName].
     */
    fun fromDatabaseName(
      databaseName: String?,
      scopes: IScopes = ScopesAdapter.getInstance(),
    ): SQLiteSpanInstrumentation =
      SQLiteSpanInstrumentation(scopes, dbMetadataFromDatabaseName(databaseName))
  }
}
