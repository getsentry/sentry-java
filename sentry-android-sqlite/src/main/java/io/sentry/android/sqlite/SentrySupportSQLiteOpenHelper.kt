package io.sentry.android.sqlite

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * The Sentry's [SentrySupportSQLiteOpenHelper], it will automatically add a span
 *  out of the active span bound to the scope for each database query.
 * It's a wrapper around an instance of [SupportSQLiteOpenHelper].
 *
 * You can wrap your custom [SupportSQLiteOpenHelper] instance with `SentrySupportSQLiteOpenHelper(myHelper)`.
 * If you're using the Sentry Android Gradle plugin, this will be applied automatically.
 *
 * Usage - wrap your custom [SupportSQLiteOpenHelper] instance in [SentrySupportSQLiteOpenHelper]
 *
 * ```
 * val openHelper = SentrySupportSQLiteOpenHelper.create(myOpenHelper)
 * ```
 *
 * If you use Room you can wrap the default [FrameworkSQLiteOpenHelperFactory]:
 *
 * ```
 * val database = Room.databaseBuilder(context, MyDatabase::class.java, "dbName")
 *     .openHelperFactory { configuration ->
 *         SentrySupportSQLiteOpenHelper.create(FrameworkSQLiteOpenHelperFactory().create(configuration))
 *     }
 *     ...
 *     .build()
 * ```
 *
 * @param delegate The [SupportSQLiteOpenHelper] instance to delegate calls to.
 */
public class SentrySupportSQLiteOpenHelper private constructor(
    private val delegate: SupportSQLiteOpenHelper
) : SupportSQLiteOpenHelper by delegate {

    private val sqLiteSpanManager = SQLiteSpanManager(databaseName = delegate.databaseName)

    private val sentryWritableDatabase: SupportSQLiteDatabase by lazy {
        SentrySupportSQLiteDatabase(delegate.writableDatabase, sqLiteSpanManager)
    }

    private val sentryReadableDatabase: SupportSQLiteDatabase by lazy {
        SentrySupportSQLiteDatabase(delegate.readableDatabase, sqLiteSpanManager)
    }

    override val writableDatabase: SupportSQLiteDatabase
        get() = sentryWritableDatabase

    override val readableDatabase: SupportSQLiteDatabase
        get() = sentryReadableDatabase

    public companion object {

        // @JvmStatic is needed to let this method be accessed by our gradle plugin
        @JvmStatic
        public fun create(delegate: SupportSQLiteOpenHelper): SupportSQLiteOpenHelper {
            return if (delegate is SentrySupportSQLiteOpenHelper) {
                delegate
            } else {
                SentrySupportSQLiteOpenHelper(delegate)
            }
        }
    }
}
