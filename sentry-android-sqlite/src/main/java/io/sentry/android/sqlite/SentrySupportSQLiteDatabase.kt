package io.sentry.android.sqlite

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.SQLException
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement

class SentrySupportSQLiteDatabase(
    private val delegate: SupportSQLiteDatabase,
    private val sqLiteSpanManager: SQLiteSpanManager
) : SupportSQLiteDatabase by delegate {

    /**
     * Compiles the given SQL statement. It will return Sentry's wrapper around SupportSQLiteStatement.
     *
     * @param sql The sql query.
     * @return Compiled statement.
     */
    override fun compileStatement(sql: String): SupportSQLiteStatement {
        return SentrySupportSQLiteStatement(delegate.compileStatement(sql), sqLiteSpanManager, sql)
    }

    /** Execute the given SQL statement on all connections to this database. */
    @Suppress("AcronymName") // To keep consistency with framework method name.
    override fun execPerConnectionSQL(
        sql: String,
        @SuppressLint("ArrayReturn") bindArgs: Array<out Any?>?
    ) {
        sqLiteSpanManager.performSql(sql) {
            delegate.execPerConnectionSQL(sql, bindArgs)
        }
    }

    override fun query(query: String): Cursor {
        return sqLiteSpanManager.performSql(query) {
            delegate.query(query)
        }
    }

    override fun query(query: String, bindArgs: Array<out Any?>): Cursor {
        return sqLiteSpanManager.performSql(query) {
            delegate.query(query, bindArgs)
        }
    }

    override fun query(query: SupportSQLiteQuery): Cursor {
        return sqLiteSpanManager.performSql(query.sql) {
            delegate.query(query)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    override fun query(
        query: SupportSQLiteQuery,
        cancellationSignal: CancellationSignal?
    ): Cursor {
        return sqLiteSpanManager.performSql(query.sql) {
            delegate.query(query, cancellationSignal)
        }
    }

    @Throws(SQLException::class)
    override fun execSQL(sql: String) {
        sqLiteSpanManager.performSql(sql) {
            delegate.execSQL(sql)
        }
    }

    @Throws(SQLException::class)
    override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
        sqLiteSpanManager.performSql(sql) {
            delegate.execSQL(sql, bindArgs)
        }
    }
}
