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

/** Shared span creation and metadata for SQLite instrumentation. */
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
    val nanoPrecisionStart = startTimestamp.repairPrecision(baseline = parent.startDate)
    val endTimestamp = SentryLongDate(nanoPrecisionStart.nanoTimestamp() + durationNanos)
    parent.recordChild(sql, nanoPrecisionStart, endTimestamp, status, throwable)
  }

  /**
   * Records a `db.sql.query` span from [startTimestamp] to the moment of invocation.
   *
   * "Coarse" in that it doesn't ensure nanosecond precision for [SentryNanotimeDate]
   * [startTimestamp]s.
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

  /**
   * Repairs the receiver's [nanoTimestamp][SentryDate.nanoTimestamp] if needed so that it actually
   * has nanosecond precision.
   *
   * Designed for use with spans whose start timestamps are [SentryNanotimeDate]s. Without repair,
   * those timestamps will be aligned to the same millisecond at transport, and the Sentry UI will
   * arbitrarily reorder them:
   * ```
   * Parent span                 ├█████████████┤
   * END TRANSACTION              ├███┤          0.18 ms  ← (Wrong order)
   * BEGIN IMMEDIATE TRANSACTION  ├████┤         0.25 ms
   * INSERT INTO `my_db` …        ├██┤           0.10 ms
   *                              ↑
   *               (All spans share the same ms baseline
   *             even though their execution was staggered)
   * ```
   *
   * Repair ensures proper ordering and lets the spans stagger:
   * ```
   * Parent span                 ├█████████████┤
   * BEGIN IMMEDIATE TRANSACTION  ├████┤         0.25 ms
   * INSERT INTO `my_db` …              ├██┤     0.10 ms
   * END TRANSACTION                     ├███┤   0.18 ms
   * ```
   */
  private fun SentryDate.repairPrecision(baseline: SentryDate?): SentryDate =
    if (baseline is SentryNanotimeDate) {
      SentryLongDate(baseline.laterDateNanosTimestampByDiff(this))
    } else {
      this
    }

  companion object {

    fun fromDatabaseName(databaseName: String?, scopes: IScopes = ScopesAdapter.getInstance()) =
      SQLiteSpanInstrumentation(scopes, dbMetadataFromDatabaseName(databaseName))

    fun fromFileName(fileName: String, scopes: IScopes = ScopesAdapter.getInstance()) =
      SQLiteSpanInstrumentation(scopes, dbMetadataFromFileName(fileName))
  }
}
