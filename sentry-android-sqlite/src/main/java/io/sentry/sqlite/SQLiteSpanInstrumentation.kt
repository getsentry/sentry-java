package io.sentry.sqlite

import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.Instrumenter
import io.sentry.ScopesAdapter
import io.sentry.SentryDate
import io.sentry.SentryLongDate
import io.sentry.SentryNanotimeDate
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

  /** Records a `db.sql.query` span from [startTimestamp] to [startTimestamp] + [durationNanos]. */
  fun recordSpan(
    sql: String,
    startTimestamp: SentryDate,
    durationNanos: Long,
    status: SpanStatus,
    throwable: Throwable? = null,
  ) {
    val parent = scopes.span ?: return
    val nanoPrecisionStart = startTimestamp.repairPrecision(anchor = parent.startDate)
    val endTimestamp = SentryLongDate(nanoPrecisionStart.nanoTimestamp() + durationNanos)
    parent.recordChild(sql, nanoPrecisionStart, endTimestamp, status, throwable)
  }

  /**
   * Records a `db.sql.query` span from [startTimestamp] to the moment of invocation.
   *
   * "Coarse" in that it doesn't try to restore nanosecond precision for the start timestamp. Spans
   * that start within the same wall clock millisecond will share the same start time and may be
   * arbitrarily re-ordered by the Sentry UI.
   */
  fun recordCoarseSpan(
    sql: String,
    startTimestamp: SentryDate,
    status: SpanStatus,
    throwable: Throwable? = null,
  ) {
    val parent = scopes.span ?: return
    parent.recordChild(sql, startTimestamp, endTimestamp = null, status, throwable)
  }

  private fun ISpan.recordChild(
    sql: String,
    startTimestamp: SentryDate,
    endTimestamp: SentryDate?,
    status: SpanStatus,
    throwable: Throwable?,
  ) {
    startChild("db.sql.query", sql, startTimestamp, Instrumenter.SENTRY).apply {
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

/**
 * Repairs the receiver's [nanoTimestamp][SentryDate.nanoTimestamp] if needed so that it actually
 * has nanosecond precision.
 *
 * Designed for use with spans whose start timestamps are [SentryNanotimeDate]s. Without repair,
 * those timestamps will be aligned to the same millisecond at transport, and the Sentry UI will
 * arbitrarily reorder them:
 * ```
 *                                  (Relative start times out of order)
 *                                                ↓
 * Parent span                 ├█████████████┤
 * END TRANSACTION              ├███┤          0.33 ms
 * BEGIN IMMEDIATE TRANSACTION  ├████┤         0.02 ms
 * INSERT INTO `my_db` …        ├██┤           0.30 ms
 *                              ↑
 *               (All spans share the same ms baseline
 *             even though their execution was staggered)
 * ```
 *
 * Repair ensures proper ordering and lets the spans stagger:
 * ```
 * Parent span                 ├█████████████┤
 * BEGIN IMMEDIATE TRANSACTION  ├████┤         0.02 ms
 * INSERT INTO `my_db` …              ├██┤     0.30 ms
 * END TRANSACTION                     ├███┤   0.33 ms
 * ```
 */
internal fun SentryDate.repairPrecision(anchor: SentryDate?): SentryDate =
  if (anchor is SentryNanotimeDate) {
    // Compute a new timestamp with nanosecond precision by using the anchor as the epoch instant
    // and adding to it the diff of this.nanos - anchor.nanos.
    SentryLongDate(anchor.laterDateNanosTimestampByDiff(this))
  } else {
    this
  }
