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
 * [SentrySupportSQLiteOpenHelper][io.sentry.android.sqlite.SentrySupportSQLiteOpenHelper] on the
 * same database file. Both wrappers instrument at different layers and combining them will produce
 * duplicate spans.
 *
 * @param delegate The [SQLiteDriver] instance to delegate calls to.
 */
internal class SentrySQLiteDriver private constructor(private val delegate: SQLiteDriver) :
  SQLiteDriver {

  init {
    SentryIntegrationPackageStorage.getInstance().addIntegration("SQLiteDriver")
  }

  override val hasConnectionPool: Boolean
    get() =
      try {
        delegate.hasConnectionPool
      } catch (_: LinkageError) {
        // Delegates on androidx.sqlite < 2.6.0 won't have a hasConnectionPool property.
        false
      }

  @Suppress("TooGenericExceptionCaught")
  override fun open(fileName: String): SQLiteConnection {
    val connection = delegate.open(fileName)

    return try {
      val spans = SQLiteSpanInstrumentation.fromFileName(fileName)
      // create() ensures delegate is unwrapped, so we don't need to protect against double-wrapping
      // the connection.
      SentrySQLiteConnection(connection, spans)
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

  companion object {

    /**
     * Wraps the provided delegate in a [SentrySQLiteDriver]. Returns the delegate as-is if already
     * wrapped.
     */
    @JvmStatic
    fun create(delegate: SQLiteDriver): SQLiteDriver =
      delegate as? SentrySQLiteDriver ?: SentrySQLiteDriver(delegate)
  }
}
