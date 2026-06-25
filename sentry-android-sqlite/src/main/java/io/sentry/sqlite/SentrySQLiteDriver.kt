package io.sentry.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import io.sentry.ScopesAdapter
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import org.jetbrains.annotations.ApiStatus

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
 * @param delegate The [SQLiteDriver] instance to delegate calls to.
 */
@ApiStatus.Experimental
public class SentrySQLiteDriver private constructor(private val delegate: SQLiteDriver) :
  SQLiteDriver {

  init {
    SentryIntegrationPackageStorage.getInstance().addIntegration("SQLiteDriver")
  }

  @Suppress("INAPPLICABLE_JVM_NAME")
  @get:JvmName("hasConnectionPool")
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
      val spans = DriverSpans.fromFileName(fileName)
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
     * Name of the bridge adapter often used with Room 2.7+. It implements the `SQLiteDriver`
     * interface and its constructor consumes a `SupportSQLiteOpenHelper`. (Users of the Sentry
     * Android Gradle Plugin will have the `SupportSQLiteOpenHelper` wrapped for them
     * automatically.) We deliberately avoid wrapping the adapter to prevent duplicate spans.
     *
     * String (rather than an `is` check) lets us avoid a compile-time dependency on
     * androidx.sqlite:sqlite-framework.
     */
    private const val SUPPORT_SQLITE_DRIVER_FQN = "androidx.sqlite.driver.SupportSQLiteDriver"

    /**
     * Wraps the provided delegate in a [SentrySQLiteDriver].
     *
     * To avoid duplicate spans, returns the delegate as-is if:
     * 1. it's already wrapped, or
     * 2. it's an `androidx.sqlite.driver.SupportSQLiteDriver`.
     *
     * In the case of (2), wrap the open helper passed to the `SupportSQLiteDriver` constructor via
     * `SentrySupportSQLiteOpenHelper` instead.
     */
    @JvmStatic
    public fun create(delegate: SQLiteDriver): SQLiteDriver =
      if (delegate is SentrySQLiteDriver || delegate.javaClass.name == SUPPORT_SQLITE_DRIVER_FQN) {
        delegate
      } else {
        SentrySQLiteDriver(delegate)
      }
  }
}
