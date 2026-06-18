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

/**
 * Sentinel for extracting a [SentryNanotimeDate]'s underlying [System.nanoTime] value via
 * [SentryDate.diff].
 */
private val EMPTY_NANO_TIME = SentryNanotimeDate(0, 0L)

/** Span instrumentation for [SentrySQLiteDriver]. */
internal class SQLiteSpanInstrumentation(
  private val scopes: IScopes,
  private val dbMetadata: DbMetadata,
) {

  private val stackTraceFactory = SentryStackTraceFactory(scopes.options)

  /**
   * Returns a timestamp in nanoseconds for use with [recordSpan]. Timestamp is ns-precise if the
   * active parent span uses a [SentryNanotimeDate] (the ordinary case); otherwise it's ms-precise.
   *
   * Note: Internalizing the start time in [recordSpan] would shift spans to end-of-work on the
   * trace timeline, which is less desirable; callers capture the start before doing database work
   * and pass it back to [recordSpan].
   */
  fun startTimestamp(): Long =
    // Try to retain nanosecond precision + avoid SentryDate allocation...
    scopes.span?.computeNanoStartTimestampForChild()
      // ...otherwise fall back to millisecond precision + allocate.
      ?: scopes.options.dateProvider.now().nanoTimestamp()

  /** Records a `db.sql.query` span. */
  fun recordSpan(
    sql: String,
    startTimestampNanos: Long,
    durationNanos: Long,
    status: SpanStatus,
    throwable: Throwable? = null,
  ) {
    val parent = scopes.span ?: return
    val startTimestamp = SentryLongDate(startTimestampNanos)
    val endTimestamp = SentryLongDate(startTimestampNanos + durationNanos)

    parent.startChild("db.sql.query", sql, startTimestamp, Instrumenter.SENTRY).apply {
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
  }
}

/**
 * Computes a start timestamp with nanosecond precision for the child of the receiver span. Returns
 * null if nanosecond precision isn't possible.
 *
 * Lets us improve the display of spans in the Sentry UI. If timestamps are only ms-precise, the
 * Sentry UI will left-align and arbitrarily reorder spans that share the same wall clock ms:
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
 * Nanosecond precision ensures proper ordering and lets the spans stagger:
 * ```
 * Parent span                 ├█████████████┤
 * BEGIN IMMEDIATE TRANSACTION  ├████┤         0.02 ms
 * INSERT INTO `my_db` …              ├██┤     0.30 ms
 * END TRANSACTION                     ├███┤   0.33 ms
 * ```
 */
internal fun ISpan.computeNanoStartTimestampForChild(): Long? {
  if (startDate !is SentryNanotimeDate) {
    return null
  }

  val parentWallClockNanos = startDate.nanoTimestamp()
  val parentMonotonicNanos = startDate.diff(EMPTY_NANO_TIME)
  val elapsedSinceParentStart = System.nanoTime() - parentMonotonicNanos
  // Return the child's absolute start time.
  return parentWallClockNanos + elapsedSinceParentStart
}
