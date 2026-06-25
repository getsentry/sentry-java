package io.sentry.android.sqlite

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.SQLException
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement

/**
 * The Sentry's [SentrySupportSQLiteDatabase], it will automatically add a span out of the active
 * span bound to the scope for each database query. It's a wrapper around [SupportSQLiteDatabase],
 * and it's created automatically by the [SentrySupportSQLiteOpenHelper].
 *
 * @param delegate The [SupportSQLiteDatabase] instance to delegate calls to.
 * @param spans The [OpenHelperSpans] manager responsible for the creation of the spans.
 */
internal class SentrySupportSQLiteDatabase(
  private val delegate: SupportSQLiteDatabase,
  private val spans: OpenHelperSpans,
) : SupportSQLiteDatabase by delegate {
  /**
   * Compiles the given SQL statement. It will return Sentry's wrapper around
   * SupportSQLiteStatement.
   *
   * @param sql The sql query.
   * @return Compiled statement.
   */
  override fun compileStatement(sql: String): SupportSQLiteStatement =
    SentrySupportSQLiteStatement(delegate.compileStatement(sql), spans, sql)

  @Suppress("AcronymName") // To keep consistency with framework method name.
  override fun execPerConnectionSQL(
    sql: String,
    @SuppressLint("ArrayReturn") bindArgs: Array<out Any?>?,
  ) {
    spans.performSql(sql) { delegate.execPerConnectionSQL(sql, bindArgs) }
  }

  override fun query(query: String): Cursor = spans.performSql(query) { delegate.query(query) }

  override fun query(query: String, bindArgs: Array<out Any?>): Cursor =
    spans.performSql(query) { delegate.query(query, bindArgs) }

  override fun query(query: SupportSQLiteQuery): Cursor =
    spans.performSql(query.sql) { delegate.query(query) }

  override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor =
    spans.performSql(query.sql) { delegate.query(query, cancellationSignal) }

  @Throws(SQLException::class)
  override fun execSQL(sql: String) {
    spans.performSql(sql) { delegate.execSQL(sql) }
  }

  @Throws(SQLException::class)
  override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
    spans.performSql(sql) { delegate.execSQL(sql, bindArgs) }
  }
}
