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

internal enum class SqlRole {
  BEGIN_TRANSACTION,
  COMMIT_TRANSACTION,
  ROLLBACK_TRANSACTION,
  PASSTHROUGH,
  QUERY,
}

/**
 * Classifies the leading keyword of [sql] after [trimStart].
 *
 * Does not strip SQL comments: a statement prefixed by a `/* … */` block comment or `-- line\n`
 * line comment classifies as [SqlRole.QUERY] and would miss transaction nesting. Room and
 * SQLDelight emit transaction-control SQL verbatim, so the gap is only reachable via hand-rolled
 * `execSQL` calls.
 */
internal fun classifySql(sql: String): SqlRole {
  val trimmed = sql.trimStart()
  if (trimmed.isEmpty()) return SqlRole.QUERY

  return when (trimmed[0].uppercaseChar()) {
    'B' ->
      if (trimmed.regionMatches(0, "BEGIN", 0, 5, ignoreCase = true)) {
        SqlRole.BEGIN_TRANSACTION
      } else {
        SqlRole.QUERY
      }
    'C' ->
      if (trimmed.regionMatches(0, "COMMIT", 0, 6, ignoreCase = true)) {
        SqlRole.COMMIT_TRANSACTION
      } else {
        SqlRole.QUERY
      }
    'E' ->
      if (trimmed.regionMatches(0, "END", 0, 3, ignoreCase = true)) {
        SqlRole.COMMIT_TRANSACTION
      } else {
        SqlRole.QUERY
      }
    'R' ->
      if (trimmed.regionMatches(0, "ROLLBACK", 0, 8, ignoreCase = true)) {
        classifyRollback(trimmed)
      } else if (trimmed.regionMatches(0, "RELEASE", 0, 7, ignoreCase = true)) {
        SqlRole.PASSTHROUGH
      } else {
        SqlRole.QUERY
      }
    'S' ->
      if (trimmed.regionMatches(0, "SAVEPOINT", 0, 9, ignoreCase = true)) {
        SqlRole.PASSTHROUGH
      } else {
        SqlRole.QUERY
      }
    else -> SqlRole.QUERY
  }
}

private fun classifyRollback(trimmed: String): SqlRole {
  var rest = trimmed.substring(8).trimStart()
  if (rest.regionMatches(0, "TRANSACTION", 0, 11, ignoreCase = true)) {
    rest = rest.substring(11).trimStart()
  }
  return if (rest.regionMatches(0, "TO", 0, 2, ignoreCase = true)) {
    SqlRole.PASSTHROUGH
  } else {
    SqlRole.ROLLBACK_TRANSACTION
  }
}

/** Shared span creation and metadata for SQLite instrumentation. */
internal class SQLiteSpanInstrumentation(
  private val scopes: IScopes,
  private val dbMetadata: DbMetadata,
) {

  private val stackTraceFactory = SentryStackTraceFactory(scopes.options)

  private var currentTransactionSpan: ISpan? = null

  /**
   * Returns a start timestamp for a `db.sql.query` span.
   *
   * Exposed so callers can capture a wall-clock start before accumulating database time.
   * Internalizing the start time in [recordSpan] would shift spans to end-of-work on the trace
   * timeline, which is less desirable.
   */
  fun startTimestamp(): SentryDate = scopes.options.dateProvider.now()

  /**
   * Records a span with transaction-aware nesting. SQL is classified to determine whether it opens,
   * closes, or is a child of a database transaction.
   *
   * Use this method for the SentrySQLiteDriver path (Room 3 / SQLDelight). The legacy
   * SentrySupportSQLiteDatabase path should continue using [recordSpan] / [recordCoarseSpan].
   */
  fun recordSpanWithTransactionTracking(
    sql: String,
    startTimestamp: SentryDate,
    durationNanos: Long,
    status: SpanStatus,
    throwable: Throwable? = null,
  ) {
    when (classifySql(sql)) {
      SqlRole.BEGIN_TRANSACTION ->
        handleBegin(sql, startTimestamp, durationNanos, status, throwable)
      SqlRole.COMMIT_TRANSACTION ->
        handleCloseTransaction(
          sql,
          startTimestamp,
          durationNanos,
          status,
          throwable,
          leafOp = "db.sql.transaction.commit",
          parentStatus = SpanStatus.OK,
        )
      SqlRole.ROLLBACK_TRANSACTION ->
        handleCloseTransaction(
          sql,
          startTimestamp,
          durationNanos,
          status,
          throwable,
          leafOp = "db.sql.transaction.rollback",
          parentStatus = SpanStatus.ABORTED,
        )
      SqlRole.PASSTHROUGH,
      SqlRole.QUERY -> handleQuery(sql, startTimestamp, durationNanos, status, throwable)
    }
  }

  /** Finishes a dangling transaction span when the connection closes without COMMIT/ROLLBACK. */
  fun finishDanglingTransaction() {
    val txnSpan = currentTransactionSpan ?: return
    currentTransactionSpan = null

    val now = scopes.options.dateProvider.now()
    txnSpan
      .startChild("db.sql.transaction.rollback", "(connection closed)", now, Instrumenter.SENTRY)
      .apply {
        spanContext.origin = SQLITE_TRACE_ORIGIN
        applyDbMetadata()
        finish(SpanStatus.OK, now)
      }

    txnSpan.finish(SpanStatus.ABORTED)
  }

  private fun handleBegin(
    sql: String,
    startTimestamp: SentryDate,
    durationNanos: Long,
    status: SpanStatus,
    throwable: Throwable?,
  ) {
    val parent = scopes.span ?: return

    if (status != SpanStatus.OK) {
      val nanoPrecisionStart = startTimestamp.repairPrecision(baseline = parent.startDate)
      val endTimestamp = SentryLongDate(nanoPrecisionStart.nanoTimestamp() + durationNanos)
      parent.recordQueryChild(sql, nanoPrecisionStart, endTimestamp, status, throwable)
      return
    }

    currentTransactionSpan?.finish(SpanStatus.ABORTED)

    val nanoPrecisionStart = startTimestamp.repairPrecision(baseline = parent.startDate)
    val txnSpan =
      parent.startChild("db.sql.transaction", sql, nanoPrecisionStart, Instrumenter.SENTRY)
    txnSpan.spanContext.origin = SQLITE_TRACE_ORIGIN
    txnSpan.applyDbMetadata()
    currentTransactionSpan = txnSpan
  }

  private fun handleCloseTransaction(
    sql: String,
    startTimestamp: SentryDate,
    durationNanos: Long,
    status: SpanStatus,
    throwable: Throwable?,
    leafOp: String,
    parentStatus: SpanStatus,
  ) {
    val txnSpan = currentTransactionSpan

    if (status != SpanStatus.OK || txnSpan == null) {
      val parent = txnSpan ?: scopes.span ?: return
      val nanoPrecisionStart = startTimestamp.repairPrecision(baseline = parent.startDate)
      val endTimestamp = SentryLongDate(nanoPrecisionStart.nanoTimestamp() + durationNanos)
      parent.recordQueryChild(sql, nanoPrecisionStart, endTimestamp, status, throwable)
      return
    }

    currentTransactionSpan = null

    val nanoPrecisionStart = startTimestamp.repairPrecision(baseline = txnSpan.startDate)
    val endTimestamp = SentryLongDate(nanoPrecisionStart.nanoTimestamp() + durationNanos)

    txnSpan.startChild(leafOp, sql, nanoPrecisionStart, Instrumenter.SENTRY).apply {
      spanContext.origin = SQLITE_TRACE_ORIGIN
      throwable?.let { this.throwable = it }
      applyDbMetadata()
      finish(status, endTimestamp)
    }

    txnSpan.finish(parentStatus, endTimestamp)
  }

  private fun handleQuery(
    sql: String,
    startTimestamp: SentryDate,
    durationNanos: Long,
    status: SpanStatus,
    throwable: Throwable?,
  ) {
    val parent = currentTransactionSpan ?: scopes.span ?: return
    val nanoPrecisionStart = startTimestamp.repairPrecision(baseline = parent.startDate)
    val endTimestamp = SentryLongDate(nanoPrecisionStart.nanoTimestamp() + durationNanos)
    parent.recordQueryChild(sql, nanoPrecisionStart, endTimestamp, status, throwable)
  }

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
    parent.recordQueryChild(sql, nanoPrecisionStart, endTimestamp, status, throwable)
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
    parent.recordQueryChild(sql, startTimestamp, endTimestamp = null, status, throwable)
  }

  private fun ISpan.recordQueryChild(
    sql: String,
    startTimestamp: SentryDate,
    endTimestamp: SentryDate?,
    status: SpanStatus,
    throwable: Throwable?,
  ) {
    startChild("db.sql.query", sql, startTimestamp, Instrumenter.SENTRY).apply {
      spanContext.origin = SQLITE_TRACE_ORIGIN
      throwable?.let { this.throwable = it }
      applyDbMetadata()
      finish(status, endTimestamp)
    }
  }

  private fun ISpan.applyDbMetadata() {
    val isMainThread = scopes.options.threadChecker.isMainThread
    setData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY, isMainThread)

    if (isMainThread) {
      setData(SpanDataConvention.CALL_STACK_KEY, stackTraceFactory.inAppCallStack)
    }

    dbMetadata.name?.let { setData(SpanDataConvention.DB_NAME_KEY, it) }
    setData(SpanDataConvention.DB_SYSTEM_KEY, dbMetadata.system)
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
