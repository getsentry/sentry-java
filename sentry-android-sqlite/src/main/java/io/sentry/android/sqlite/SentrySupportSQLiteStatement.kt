package io.sentry.android.sqlite

import android.database.SQLException
import androidx.sqlite.db.SupportSQLiteStatement
import io.sentry.Sentry
import io.sentry.SpanStatus

class SentrySupportSQLiteStatement(private val delegate: SupportSQLiteStatement): SupportSQLiteStatement by delegate {

    private val sqLiteSpanManager = SQLiteSpanManager()


    /**
     * Execute this SQL statement, if it is not a SELECT / INSERT / DELETE / UPDATE, for example
     * CREATE / DROP table, view, trigger, index etc.
     *
     * @throws [android.database.SQLException] If the SQL string is invalid for
     * some reason
     */
    override fun execute() {
        return sqLiteSpanManager.performSql {
            delegate.execute()
        }
    }

    /**
     * Execute this SQL statement, if the the number of rows affected by execution of this SQL
     * statement is of any importance to the caller - for example, UPDATE / DELETE SQL statements.
     *
     * @return the number of rows affected by this SQL statement execution.
     * @throws [android.database.SQLException] If the SQL string is invalid for
     * some reason
     */
    override fun executeUpdateDelete(): Int {
        return sqLiteSpanManager.performSql {
            delegate.executeUpdateDelete()
        }
    }

    /**
     * Execute this SQL statement and return the ID of the row inserted due to this call.
     * The SQL statement should be an INSERT for this to be a useful call.
     *
     * @return the row ID of the last row inserted, if this insert is successful. -1 otherwise.
     *
     * @throws [android.database.SQLException] If the SQL string is invalid for
     * some reason
     */
    override fun executeInsert(): Long {
        return sqLiteSpanManager.performSql {
            delegate.executeInsert()
        }
    }

    /**
     * Execute a statement that returns a 1 by 1 table with a numeric value.
     * For example, SELECT COUNT(*) FROM table;
     *
     * @return The result of the query.
     *
     * @throws [android.database.sqlite.SQLiteDoneException] if the query returns zero rows
     */
    override fun simpleQueryForLong(): Long {
        return sqLiteSpanManager.performSql {
            delegate.simpleQueryForLong()
        }
    }

    /**
     * Execute a statement that returns a 1 by 1 table with a text value.
     * For example, SELECT COUNT(*) FROM table;
     *
     * @return The result of the query.
     *
     * @throws [android.database.sqlite.SQLiteDoneException] if the query returns zero rows
     */
    override fun simpleQueryForString(): String? {
        return sqLiteSpanManager.performSql {
            delegate.simpleQueryForString()
        }
    }
}
