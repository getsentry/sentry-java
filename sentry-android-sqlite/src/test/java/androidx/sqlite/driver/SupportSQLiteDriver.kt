package androidx.sqlite.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver

/**
 * Minimal stub of `androidx.sqlite.driver.SupportSQLiteDriver` (which lives in
 * `androidx.sqlite:sqlite-framework`, not on this module's compile/test classpath) for verifying
 * behavior of `SentrySQLiteDriver.create(SupportSQLiteDriver)`.
 *
 * The production check is `delegate.javaClass.name ==
 * "androidx.sqlite.driver.SupportSQLiteDriver"`, so any class with this exact fully-qualified name
 * exercises the branch.
 */
internal class SupportSQLiteDriver : SQLiteDriver {

  override val hasConnectionPool: Boolean = false

  override fun open(fileName: String): SQLiteConnection {
    throw UnsupportedOperationException("Test stub; not for runtime use")
  }
}
