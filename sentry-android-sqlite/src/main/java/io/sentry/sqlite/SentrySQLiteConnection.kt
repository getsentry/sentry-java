package io.sentry.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

internal class SentrySQLiteConnection(
  private val delegate: SQLiteConnection,
  private val spans: DriverSpans,
) : SQLiteConnection by delegate {

  override fun prepare(sql: String): SQLiteStatement {
    val statement = delegate.prepare(sql)
    return statement as? SentrySQLiteStatement ?: SentrySQLiteStatement(statement, spans, sql)
  }
}
