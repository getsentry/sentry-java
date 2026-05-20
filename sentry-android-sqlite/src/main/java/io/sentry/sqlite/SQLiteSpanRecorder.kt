package io.sentry.sqlite

import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryDate
import io.sentry.SentryLevel
import io.sentry.SentryLongDate
import io.sentry.SpanStatus

internal class SQLiteSpanRecorder(
  fileName: String,
  private val scopes: IScopes = ScopesAdapter.getInstance(),
) {

  private val spanHelper = SQLiteSpanHelper(scopes, dbMetadataFromFileName(fileName))

  /**
   * Returns a start timestamp for a db.sql.query span.
   *
   * Exposed so callers can capture a wall-clock start before accumulating database time.
   * Internalizing the start time in [recordSpan] would shift spans to end-of-work on the trace
   * timeline, which is less desirable.
   */
  fun startTimestamp(): SentryDate = scopes.options.dateProvider.now()

  /** Records a db.sql.query span. */
  @Suppress("TooGenericExceptionCaught")
  fun recordSpan(
    sql: String,
    startTimestamp: SentryDate,
    durationNanos: Long,
    status: SpanStatus,
    throwable: Throwable? = null,
  ) {
    try {
      val span = spanHelper.startSpan(sql, startTimestamp) ?: return
      throwable?.let { span.throwable = it }
      spanHelper.applyDataToSpan(span)
      val endTimestamp = SentryLongDate(startTimestamp.nanoTimestamp() + durationNanos)
      span.finish(status, endTimestamp)
    } catch (t: Throwable) {
      scopes.options.logger.log(SentryLevel.ERROR, "Failed to record SQLite span.", t)
    }
  }
}
