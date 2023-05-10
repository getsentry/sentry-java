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

class SentrySupportSQLiteDatabase(private val delegate: SupportSQLiteDatabase, private val sqLiteSpanManager: SQLiteSpanManager): SupportSQLiteDatabase by delegate {


    /**
     * Compiles the given SQL statement. It will return Sentry's wrapper around SupportSQLiteStatement.
     *
     * @param sql The sql query.
     * @return Compiled statement.
     */
    override fun compileStatement(sql: String): SupportSQLiteStatement {
        return SentrySupportSQLiteStatement(delegate.compileStatement(sql), sqLiteSpanManager, sql)
    }

    override fun beginTransaction() {
        sqLiteSpanManager.beginTransaction()
        delegate.beginTransaction()
    }

    override fun beginTransactionNonExclusive() {
        sqLiteSpanManager.beginTransaction()
        delegate.beginTransactionNonExclusive()
    }

    override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) {
        sqLiteSpanManager.beginTransaction()
        delegate.beginTransactionNonExclusive()
    }

    override fun beginTransactionWithListenerNonExclusive(
        transactionListener: SQLiteTransactionListener
    ) {
        sqLiteSpanManager.beginTransaction()
        delegate.beginTransactionNonExclusive()
    }

    override fun endTransaction() {
        delegate.endTransaction()
        sqLiteSpanManager.endTransaction()
    }

    override fun setTransactionSuccessful() {
        delegate.setTransactionSuccessful()
        sqLiteSpanManager.setTransactionSuccessful()
    }

    /** Execute the given SQL statement on all connections to this database. */
    @Suppress("AcronymName") // To keep consistency with framework method name.
    override fun execPerConnectionSQL(
        sql: String,
        @SuppressLint("ArrayReturn") bindArgs: Array<out Any?>?
    ) {
        sqLiteSpanManager.performSql("execPerConnectionSQL", sql) {
            delegate.execPerConnectionSQL(sql, bindArgs)
        }
    }

    override fun query(query: String): Cursor {
        return sqLiteSpanManager.performSql("query", query) {
            delegate.query(query)
        }
    }

    override fun query(query: String, bindArgs: Array<out Any?>): Cursor {
        return sqLiteSpanManager.performSql("query", query) {
            delegate.query(query, bindArgs)
        }
    }

    override fun query(query: SupportSQLiteQuery): Cursor {
        return sqLiteSpanManager.performSql("query", query.sql) {
            delegate.query(query)
        }
    }

    override fun query(
        query: SupportSQLiteQuery,
        cancellationSignal: CancellationSignal?
    ): Cursor {
        return sqLiteSpanManager.performSql("query", query.sql) {
            delegate.query(query, cancellationSignal)
        }
    }

    @Throws(SQLException::class)
    override fun execSQL(sql: String) {
        sqLiteSpanManager.performSql("execSql", sql) {
            delegate.execSQL(sql)
        }
    }

    @Throws(SQLException::class)
    override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
        sqLiteSpanManager.performSql("execSql", sql) {
            delegate.execSQL(sql, bindArgs)
        }
    }

}

 todo add tests for SQLiteSpanManager SentrySupportSQLiteStatement SentrySupportSQLiteOpenHelper SentrySupportSQLiteDatabase
