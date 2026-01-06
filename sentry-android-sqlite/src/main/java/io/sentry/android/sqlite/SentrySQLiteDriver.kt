package io.sentry.android.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import io.sentry.android.sqlite.SQLiteSpanManager
import io.sentry.IScopes
import io.sentry.ScopesAdapter


/**
 * Automatically adds a Sentry span to the current scope for each database query executed.
 *
 * Usage - wrap this around your current [SQLiteDriver]:
 * ```
 * val driver = SentrySQLiteDriver.create(AndroidSQLiteDriver())
 * ```
 *
 * If you use Room you can wrap the default [AndroidSQLiteDriver]:
 * ```
 * val database = Room.databaseBuilder(context, MyDatabase::class.java, "dbName")
 *     .setDriver(SentrySQLiteDriver.create(AndroidSQLiteDriver()))
 *     ...
 *     .build()
 * ```
 */
public class SentrySQLiteDriver internal constructor(
  private val delegate: SQLiteDriver,
  private val scopes: IScopes = ScopesAdapter.getInstance(),
) : SQLiteDriver {
  override fun open(fileName: String): SQLiteConnection {
    val sqliteSpanManager = SQLiteSpanManager(
      scopes,
      // SQLiteDriver.open docs say:
      // >> To open an in-memory database use the special name :memory: as the fileName.
      // SQLiteSpanManager expects null for an in-memory databaseName, so replace ":memory:" with null.
      databaseName = fileName.takeIf { it != ":memory:" }
    )
    val connection = delegate.open(fileName)
    return SentrySQLiteConnection(connection, sqliteSpanManager)
  }

  public companion object {
    /**
     * @param delegate The [SQLiteDriver] instance to delegate calls to.
     */
    @JvmStatic
    public fun create(delegate: SQLiteDriver): SQLiteDriver {
      if (delegate is SentrySQLiteDriver) {
        return delegate
      } else {
        return SentrySQLiteDriver(delegate)
      }
    }
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
