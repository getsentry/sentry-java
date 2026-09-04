package io.sentry.sqlite

import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.Instrumenter
import io.sentry.ScopesAdapter
import io.sentry.SentryLongDate
import io.sentry.SentryStackTraceFactory
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus

private const val SQLITE_TRACE_ORIGIN = "auto.db.sqlite"

/** Span instrumentation for [SentrySQLiteDriver]. */
internal class DriverSpans(private val scopes: IScopes, private val dbMetadata: DbMetadata) {

  private val stackTraceFactory = SentryStackTraceFactory(scopes.options)

  /**
   * Returns a timestamp in nanoseconds for use with [record]. Timestamp is ns-precise if the active
   * parent span is anchored (the ordinary case); otherwise it's ms-precise.
   *
   * Note: Internalizing the start time in [record] would shift spans to end-of-work on the trace
   * timeline, which is less desirable; callers capture the start before doing database work and
   * pass it back to [record].
   */
  fun startTimestamp(): Long =
    // Try to retain nanosecond precision + avoid SentryDate allocation...
    scopes.span?.computeNanoStartTimestampForChild()
      // ...otherwise fall back to millisecond precision + allocate.
      ?: scopes.options.dateProvider.now().nanoTimestamp()

  /** Records a `db.sql.query` span. */
  fun record(
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
     * Returns [DriverSpans] based on the [fileName] argument passed to
     * [SQLiteDriver.open][androidx.sqlite.SQLiteDriver.open].
     */
    fun fromFileName(fileName: String, scopes: IScopes = ScopesAdapter.getInstance()): DriverSpans =
      DriverSpans(scopes, dbMetadataFromFileName(fileName))
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
internal fun ISpan.computeNanoStartTimestampForChild(): Long? =
  // An anchored span projects nanosecond instants from the transaction's single wall-clock
  // reading, so "now" on its own timeline is exactly where a child span should start. No
  // reconstruction, and no silent drop to millisecond precision when the parent's date happens
  // not to be anchored.
  anchor()?.now()?.epochNanos()
