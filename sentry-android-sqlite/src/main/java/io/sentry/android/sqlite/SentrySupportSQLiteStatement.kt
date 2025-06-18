package io.sentry.android.sqlite

import androidx.sqlite.db.SupportSQLiteStatement

/**
 * The Sentry's [SentrySupportSQLiteStatement], it will automatically add a span
 *  out of the active span bound to the scope when it is executed.
 * It's a wrapper around an instance of [SupportSQLiteStatement], and it's created automatically
 *  by [SentrySupportSQLiteDatabase.compileStatement].
 *
 * @param delegate The [SupportSQLiteStatement] instance to delegate calls to.
 * @param sqLiteSpanManager The [SQLiteSpanManager] responsible for the creation of the spans.
 * @param sql The query string.
 */
internal class SentrySupportSQLiteStatement(
    private val delegate: SupportSQLiteStatement,
    private val sqLiteSpanManager: SQLiteSpanManager,
    private val sql: String,
) : SupportSQLiteStatement by delegate {
    override fun execute() =
        sqLiteSpanManager.performSql(sql) {
            delegate.execute()
        }

    override fun executeUpdateDelete(): Int =
        sqLiteSpanManager.performSql(sql) {
            delegate.executeUpdateDelete()
        }

    override fun executeInsert(): Long =
        sqLiteSpanManager.performSql(sql) {
            delegate.executeInsert()
        }

    override fun simpleQueryForLong(): Long =
        sqLiteSpanManager.performSql(sql) {
            delegate.simpleQueryForLong()
        }

    override fun simpleQueryForString(): String? =
        sqLiteSpanManager.performSql(sql) {
            delegate.simpleQueryForString()
        }
}
