package io.sentry.android.sqlite

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration

class SentrySupportSQLiteOpenHelper(private val delegate: SupportSQLiteOpenHelper): SupportSQLiteOpenHelper by delegate {

    override val writableDatabase: SupportSQLiteDatabase
        get() = SentrySupportSQLiteDatabase(delegate.writableDatabase)

    /**
     * Factory class to create instances of [SupportSQLiteOpenHelper] using
     * [Configuration].
     */
    fun interface Factory {
        /**
         * Creates an instance of [SupportSQLiteOpenHelper] using the given configuration.
         *
         * @param configuration The configuration to use while creating the open helper.
         *
         * @return A SupportSQLiteOpenHelper which can be used to open a database.
         */
        fun create(configuration: Configuration): SupportSQLiteOpenHelper
    }
}
