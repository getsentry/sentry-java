package io.sentry.android.sqlite

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

class SentrySupportSQLiteOpenHelper(
    private val delegate: SupportSQLiteOpenHelper
) : SupportSQLiteOpenHelper by delegate {

    private val sqLiteSpanManager = SQLiteSpanManager()

    private val sentryDatabase: SupportSQLiteDatabase by lazy {
        SentrySupportSQLiteDatabase(delegate.writableDatabase, sqLiteSpanManager)
    }

    override val writableDatabase: SupportSQLiteDatabase
        get() = sentryDatabase

    companion object {

        fun create(delegate: SupportSQLiteOpenHelper): SupportSQLiteOpenHelper {
            return if (delegate is SentrySupportSQLiteOpenHelper) {
                delegate
            } else {
                SentrySupportSQLiteOpenHelper(delegate)
            }
        }
    }
}
