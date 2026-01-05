package io.sentry.android.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import io.sentry.android.sqlite.SQLiteSpanManager


public class SentrySQLiteDriver(
  private val delegate: SQLiteDriver,
) : SQLiteDriver {
  override fun open(fileName: String): SQLiteConnection {
    val sqliteSpanManager = SQLiteSpanManager(
      // SQLiteDriver.open docs say:
      // >> To open an in-memory database use the special name :memory: as the fileName.
      // SQLiteSpanManager expects null for an in-memory databaseName, so replace ":memory:" with null.
      databaseName = fileName.takeIf { it != ":memory:" }
    )
    val connection = delegate.open(fileName)
    return SentrySQLiteConnection(connection, sqliteSpanManager)
  }
}

internal class SentrySQLiteConnection(
  private val delegate: SQLiteConnection,
  private val sqliteSpanManager: SQLiteSpanManager,
) : SQLiteConnection by delegate {
  override fun prepare(sql: String): SQLiteStatement {
    val statement = delegate.prepare(sql)
    return SentrySQLiteStatement(statement, sqliteSpanManager, sql)
  }
}

internal class SentrySQLiteStatement(
  private val delegate: SQLiteStatement,
  private val sqliteSpanManager: SQLiteSpanManager,
  private val sql: String,
) : SQLiteStatement by delegate {
  // We have to start the span only the first time, regardless of how many times its methods get
  // called.
  private var isSpanStarted = false

  override fun step(): Boolean {
    if (isSpanStarted) {
      return delegate.step()
    } else {
      isSpanStarted = true
      return sqliteSpanManager.performSql(sql) { delegate.step() }
    }
  }

  override fun reset() {
    isSpanStarted = false
    delegate.reset()
  }
}
