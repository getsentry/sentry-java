package io.sentry.sqlite

import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.Instrumenter
import io.sentry.SentryDate
import io.sentry.SentryStackTraceFactory
import io.sentry.SpanDataConvention

private const val SQLITE_TRACE_ORIGIN = "auto.db.sqlite"

/** Shared span creation and metadata for SQLite instrumentation. */
internal class SQLiteSpanHelper(private val scopes: IScopes, private val dbMetadata: DbMetadata) {

  private val stackTraceFactory = SentryStackTraceFactory(scopes.options)

  fun startSpan(sql: String, startTimestamp: SentryDate): ISpan? =
    scopes.span?.startChild("db.sql.query", sql, startTimestamp, Instrumenter.SENTRY)?.apply {
      spanContext.origin = SQLITE_TRACE_ORIGIN
    }

  fun applyDataToSpan(span: ISpan) {
    val isMainThread = scopes.options.threadChecker.isMainThread
    span.setData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY, isMainThread)

    if (isMainThread) {
      span.setData(SpanDataConvention.CALL_STACK_KEY, stackTraceFactory.inAppCallStack)
    }

    dbMetadata.name?.let { span.setData(SpanDataConvention.DB_NAME_KEY, it) }
    span.setData(SpanDataConvention.DB_SYSTEM_KEY, dbMetadata.system)
  }
}
