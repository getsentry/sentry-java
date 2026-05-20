package io.sentry.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import io.sentry.ScopesAdapter
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel

/**
 * Wraps a [SQLiteDriver] and automatically adds spans for each SQL statement it executes.
 *
 * Example usage:
 * ```
 * val driver = SentrySQLiteDriver.create(AndroidSQLiteDriver())
 * ```
 *
 * If you use Room:
 * ```
 * val database = Room.databaseBuilder(context, MyDatabase::class.java, "dbName")
 *     .setDriver(SentrySQLiteDriver.create(AndroidSQLiteDriver()))
 *     .build()
 * ```
 *
 * **Warning:** Do not use [SentrySQLiteDriver] together with
 * [io.sentry.android.sqlite.SentrySupportSQLiteOpenHelper] on the same database file. Both wrappers
 * instrument at different layers, so combining them will produce duplicate spans for every SQL
 * statement.
 *
 * @param delegate The [SQLiteDriver] instance to delegate calls to.
 */
public class SentrySQLiteDriver private constructor(private val delegate: SQLiteDriver) :
  SQLiteDriver {

  init {
    SentryIntegrationPackageStorage.getInstance().addIntegration("SQLiteDriver")
  }

  @Suppress("TooGenericExceptionCaught")
  override fun open(fileName: String): SQLiteConnection {
    val connection = delegate.open(fileName)

    return try {
      val spanRecorder = SQLiteSpanRecorder(fileName)
      // create() ensures delegate is unwrapped, so we don't protect against double-wrapping the
      // connection.
      SentrySQLiteConnection(connection, spanRecorder)
    } catch (t: Throwable) {
      ScopesAdapter.getInstance()
        .options
        .logger
        .log(
          SentryLevel.ERROR,
          "Failed to instrument SQLite connection; returning uninstrumented connection.",
          t,
        )
      connection
    }
  }

  public companion object {

    @JvmStatic
    public fun create(delegate: SQLiteDriver): SQLiteDriver =
      delegate as? SentrySQLiteDriver ?: SentrySQLiteDriver(delegate)
  }
}
