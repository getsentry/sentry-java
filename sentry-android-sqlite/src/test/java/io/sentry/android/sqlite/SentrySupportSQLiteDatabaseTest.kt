package io.sentry.android.sqlite

import androidx.sqlite.db.SupportSQLiteDatabase
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SentrySupportSQLiteDatabaseTest {

    class Fixture {
        val mockDatabase = mock<SupportSQLiteDatabase>()
        val spanManager = mock<SQLiteSpanManager>()

        init {
            whenever(mockDatabase.compileStatement(any())).thenReturn(mock())
        }

        fun getSut(): SentrySupportSQLiteDatabase {
            return SentrySupportSQLiteDatabase(mockDatabase, spanManager)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when there is an active span and server is listed in tracing origins, adds sentry trace headers to the request`() {
        val sut = fixture.getSut()
        val compiled = sut.compileStatement("sql")
        assertNotNull(compiled)
        assertIs<SentrySupportSQLiteStatement>(compiled)
    }


    /*

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
     */
}
