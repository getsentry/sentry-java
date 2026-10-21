package io.sentry.android.sqlite

import androidx.sqlite.db.SupportSQLiteStatement

/**
 * The Sentry's [SentrySupportSQLiteStatement], it will automatically add a span out of the active
 * span bound to the scope when it is executed. It's a wrapper around an instance of
 * [SupportSQLiteStatement], and it's created automatically by
 * [SentrySupportSQLiteDatabase.compileStatement].
 *
 * @param delegate The [SupportSQLiteStatement] instance to delegate calls to.
 * @param spans The [OpenHelperSpans] manager responsible for the creation of the spans.
 * @param sql The query string.
 */
internal class SentrySupportSQLiteStatement(
  private val delegate: SupportSQLiteStatement,
  private val spans: OpenHelperSpans,
  private val sql: String,
) : SupportSQLiteStatement by delegate {
  override fun execute() = spans.performSql(sql) { delegate.execute() }

  override fun executeUpdateDelete(): Int = spans.performSql(sql) { delegate.executeUpdateDelete() }

  override fun executeInsert(): Long = spans.performSql(sql) { delegate.executeInsert() }

  override fun simpleQueryForLong(): Long = spans.performSql(sql) { delegate.simpleQueryForLong() }

  override fun simpleQueryForString(): String? =
    spans.performSql(sql) { delegate.simpleQueryForString() }
}
