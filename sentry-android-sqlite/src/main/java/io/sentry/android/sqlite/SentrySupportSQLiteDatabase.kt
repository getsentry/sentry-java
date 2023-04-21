package io.sentry.android.sqlite

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteTransactionListener
import android.os.Build
import android.os.CancellationSignal
import android.util.Pair
import androidx.annotation.RequiresApi
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import java.util.Locale

class SentrySupportSQLiteDatabase(private val delegate: SupportSQLiteDatabase): SupportSQLiteDatabase by delegate {
    /**
     * Compiles the given SQL statement.
     *
     * @param sql The sql query.
     * @return Compiled statement.
     */
    fun compileStatement(sql: String): SupportSQLiteStatement

    /**
     * Begins a transaction in EXCLUSIVE mode.
     *
     * Transactions can be nested.
     * When the outer transaction is ended all of
     * the work done in that transaction and all of the nested transactions will be committed or
     * rolled back. The changes will be rolled back if any transaction is ended without being
     * marked as clean (by calling setTransactionSuccessful). Otherwise they will be committed.
     *
     * Here is the standard idiom for transactions:
     *
     * ```
     *  db.beginTransaction()
     *  try {
     *      ...
     *      db.setTransactionSuccessful()
     *  } finally {
     *      db.endTransaction()
     *  }
     * ```
     */
    fun beginTransaction()

    /**
     * Begins a transaction in IMMEDIATE mode. Transactions can be nested. When
     * the outer transaction is ended all of the work done in that transaction
     * and all of the nested transactions will be committed or rolled back. The
     * changes will be rolled back if any transaction is ended without being
     * marked as clean (by calling setTransactionSuccessful). Otherwise they
     * will be committed.
     *
     * Here is the standard idiom for transactions:
     *
     * ```
     *  db.beginTransactionNonExclusive()
     *  try {
     *      ...
     *      db.setTransactionSuccessful()
     *  } finally {
     *      db.endTransaction()
     *  }
     *  ```
     */
    fun beginTransactionNonExclusive()

    /**
     * Begins a transaction in EXCLUSIVE mode.
     *
     * Transactions can be nested.
     * When the outer transaction is ended all of
     * the work done in that transaction and all of the nested transactions will be committed or
     * rolled back. The changes will be rolled back if any transaction is ended without being
     * marked as clean (by calling setTransactionSuccessful). Otherwise they will be committed.
     *
     * Here is the standard idiom for transactions:
     *
     * ```
     *  db.beginTransactionWithListener(listener)
     *  try {
     *      ...
     *      db.setTransactionSuccessful()
     *  } finally {
     *      db.endTransaction()
     *  }
     * ```
     *
     * @param transactionListener listener that should be notified when the transaction begins,
     * commits, or is rolled back, either explicitly or by a call to
     * [yieldIfContendedSafely].
     */
    fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener)

    /**
     * Begins a transaction in IMMEDIATE mode. Transactions can be nested. When
     * the outer transaction is ended all of the work done in that transaction
     * and all of the nested transactions will be committed or rolled back. The
     * changes will be rolled back if any transaction is ended without being
     * marked as clean (by calling setTransactionSuccessful). Otherwise they
     * will be committed.
     *
     * Here is the standard idiom for transactions:
     *
     * ```
     *  db.beginTransactionWithListenerNonExclusive(listener)
     *  try {
     *      ...
     *      db.setTransactionSuccessful()
     *  } finally {
     *      db.endTransaction()
     *  }
     * ```
     *
     * @param transactionListener listener that should be notified when the
     * transaction begins, commits, or is rolled back, either
     * explicitly or by a call to [yieldIfContendedSafely].
     */
    fun beginTransactionWithListenerNonExclusive(
        transactionListener: SQLiteTransactionListener
    )

    /**
     * End a transaction. See beginTransaction for notes about how to use this and when transactions
     * are committed and rolled back.
     */
    fun endTransaction()

    /**
     * Marks the current transaction as successful. Do not do any more database work between
     * calling this and calling endTransaction. Do as little non-database work as possible in that
     * situation too. If any errors are encountered between this and endTransaction the transaction
     * will still be committed.
     *
     * @throws IllegalStateException if the current thread is not in a transaction or the
     * transaction is already marked as successful.
     */
    fun setTransactionSuccessful()

    /**
     * Returns true if the current thread has a transaction pending.
     *
     * @return True if the current thread is in a transaction.
     */
    fun inTransaction(): Boolean

    /**
     * Execute the given SQL statement on all connections to this database.
     *
     * This statement will be immediately executed on all existing connections,
     * and will be automatically executed on all future connections.
     *
     * Some example usages are changes like `PRAGMA trusted_schema=OFF` or
     * functions like `SELECT icu_load_collation()`. If you execute these
     * statements using [execSQL] then they will only apply to a single
     * database connection; using this method will ensure that they are
     * uniformly applied to all current and future connections.
     *
     * An implementation of [SupportSQLiteDatabase] might not support this operation. Use
     * [isExecPerConnectionSQLSupported] to check if this operation is supported before
     * calling this method.
     *
     * @param sql The SQL statement to be executed. Multiple statements
     * separated by semicolons are not supported.
     * @param bindArgs The arguments that should be bound to the SQL statement.
     * @throws UnsupportedOperationException if this operation is not supported. To check if it
     * supported use [isExecPerConnectionSQLSupported]
     */
    @Suppress("AcronymName") // To keep consistency with framework method name.
    fun execPerConnectionSQL(
        sql: String,
        @SuppressLint("ArrayReturn") bindArgs: Array<out Any?>?
    ) {
        throw UnsupportedOperationException()
    }

    /**
     * Runs the given query on the database. If you would like to have typed bind arguments,
     * use [query].
     *
     * @param query The SQL query that includes the query and can bind into a given compiled
     * program.
     * @return A [Cursor] object, which is positioned before the first entry. Note that
     * [Cursor]s are not synchronized, see the documentation for more details.
     */
    fun query(query: String): Cursor

    /**
     * Runs the given query on the database. If you would like to have bind arguments,
     * use [query].
     *
     * @param query    The SQL query that includes the query and can bind into a given compiled
     * program.
     * @param bindArgs The query arguments to bind.
     * @return A [Cursor] object, which is positioned before the first entry. Note that
     * [Cursor]s are not synchronized, see the documentation for more details.
     */
    fun query(query: String, bindArgs: Array<out Any?>): Cursor

    /**
     * Runs the given query on the database.
     *
     * This class allows using type safe sql program bindings while running queries.
     *
     * @param query The [SimpleSQLiteQuery] query that includes the query and can bind into a
     * given compiled program.
     * @return A [Cursor] object, which is positioned before the first entry. Note that
     * [Cursor]s are not synchronized, see the documentation for more details.
     */
    fun query(query: SupportSQLiteQuery): Cursor

    /**
     * Runs the given query on the database.
     *
     * This class allows using type safe sql program bindings while running queries.
     *
     * @param query The SQL query that includes the query and can bind into a given compiled
     * program.
     * @param cancellationSignal A signal to cancel the operation in progress, or null if none.
     * If the operation is canceled, then [androidx.core.os.OperationCanceledException] will be
     * thrown when the query is executed.
     * @return A [Cursor] object, which is positioned before the first entry. Note that
     * [Cursor]s are not synchronized, see the documentation for more details.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    fun query(
        query: SupportSQLiteQuery,
        cancellationSignal: CancellationSignal?
    ): Cursor

    /**
     * Convenience method for inserting a row into the database.
     *
     * @param table          the table to insert the row into
     * @param values         this map contains the initial column values for the
     * row. The keys should be the column names and the values the
     * column values
     * @param conflictAlgorithm for insert conflict resolver. One of
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_NONE],
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_ROLLBACK],
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT],
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL],
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE],
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE].
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     * @throws SQLException If the insert fails
     */
    @Throws(SQLException::class)
    fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long

    /**
     * Convenience method for deleting rows in the database.
     *
     * @param table       the table to delete from
     * @param whereClause the optional WHERE clause to apply when deleting.
     * Passing null will delete all rows.
     * @param whereArgs   You may include ?s in the where clause, which
     * will be replaced by the values from whereArgs. The values
     * will be bound as Strings.
     * @return the number of rows affected if a whereClause is passed in, 0
     * otherwise. To remove all rows and get a count pass "1" as the
     * whereClause.
     */
    fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int

    /**
     * Convenience method for updating rows in the database.
     *
     * @param table       the table to update in
     * @param conflictAlgorithm for update conflict resolver. One of
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_NONE],
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_ROLLBACK],
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT],
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL],
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE],
     * [android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE].
     * @param values      a map from column names to new column values. null is a
     * valid value that will be translated to NULL.
     * @param whereClause the optional WHERE clause to apply when updating.
     * Passing null will update all rows.
     * @param whereArgs   You may include ?s in the where clause, which
     * will be replaced by the values from whereArgs. The values
     * will be bound as Strings.
     * @return the number of rows affected
     */
    fun update(
        table: String,
        conflictAlgorithm: Int,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<out Any?>?
    ): Int

    /**
     * Execute a single SQL statement that does not return any data.
     *
     * When using [enableWriteAheadLogging], journal_mode is
     * automatically managed by this class. So, do not set journal_mode
     * using "PRAGMA journal_mode" statement if your app is using
     * [enableWriteAheadLogging]
     *
     * @param sql the SQL statement to be executed. Multiple statements separated by semicolons are
     * not supported.
     * @throws SQLException if the SQL string is invalid
     */
    @Throws(SQLException::class)
    fun execSQL(sql: String)

    /**
     * Execute a single SQL statement that does not return any data.
     *
     * When using [enableWriteAheadLogging], journal_mode is
     * automatically managed by this class. So, do not set journal_mode
     * using "PRAGMA journal_mode" statement if your app is using
     * [enableWriteAheadLogging]
     *
     * @param sql the SQL statement to be executed. Multiple statements separated by semicolons
     * are not supported.
     * @param bindArgs only byte[], String, Long and Double are supported in selectionArgs.
     * @throws SQLException if the SQL string is invalid
     */
    @Throws(SQLException::class)
    fun execSQL(sql: String, bindArgs: Array<out Any?>)

}
