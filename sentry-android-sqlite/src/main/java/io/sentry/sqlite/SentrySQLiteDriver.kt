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
 * Note: In order to avoid duplicate spans, wrapping no-ops in the case of the
 * `androidx.sqlite.driver.SupportSQLiteDriver`. Wrap the open helper passed to its constructor via
 * `SentrySupportSQLiteOpenHelper` instead.
 *
 * @param delegate The [SQLiteDriver] instance to delegate calls to.
 */
public class SentrySQLiteDriver private constructor(private val delegate: SQLiteDriver) :
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

  public companion object {

    /**
     * Fully-qualified class name of the bridge adapter often used with Room 2.7+. It implements the
     * `SQLiteDriver` interface and its constructor consumes a `SupportSQLiteOpenHelper`. (Users of
     * the Sentry Android Gradle Plugin will have the `SupportSQLiteOpenHelper` wrapped for them
     * automatically.) We deliberately avoid wrapping the adapter to prevent duplicate spans.
     */
    private const val SUPPORT_SQLITE_DRIVER_FQN = "androidx.sqlite.driver.SupportSQLiteDriver"

    @JvmStatic
    public fun create(delegate: SQLiteDriver): SQLiteDriver =
      // String rather than an `is` check for SupportSQLiteDriver to avoid a compile-time dependency
      // on androidx.sqlite:sqlite-framework.
      if (delegate is SentrySQLiteDriver || delegate.javaClass.name == SUPPORT_SQLITE_DRIVER_FQN) {
        delegate
      } else {
        SentrySQLiteDriver(delegate)
      }
  }
}
