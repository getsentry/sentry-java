package io.sentry.samples.android.sqlite

import android.content.Context
import androidx.room.Room
import androidx.room3.Room as Room3
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.sentry.android.sqlite.SentrySupportSQLiteOpenHelper
import io.sentry.samples.android.sqlite.SampleDatabases.driverDirectLock
import io.sentry.samples.android.sqlite.SampleDatabases.openHelperDirectLock
import io.sentry.samples.android.sqlite.SampleDatabases.reset
import io.sentry.samples.android.sqlite.SampleDatabases.warmUp
import io.sentry.sqlite.SentrySQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-lifetime holder for the demo databases used by [SQLiteActivity].
 *
 * Real apps open a database once (commonly a DI singleton) and keep it open for the process, so a
 * screen that touches the DB almost always finds it already "warm". We model that here: [warmUp] is
 * called from `MyApplication` at launch, off the main thread, so the one-time open + Room
 * connection-pool bootstrap happens with no active transaction — those `db.sql.query` spans have
 * nothing to attach to and are dropped. Every screen afterward reuses the warm handle and records
 * only its statements of interest.
 *
 * Handles are held for the whole process: Android has no reliable "app closed" callback, and the OS
 * reclaims the connections on process death, so we never close them except via [reset] (the "Drop
 * all tables" button), which closes, deletes the files, and re-warms.
 *
 * The two "direct" handles wrap a single raw connection that isn't safe for concurrent use, so
 * callers serialize their whole unit of work via [driverDirectLock] / [openHelperDirectLock]. Room
 * and SQLDelight manage their own connection pools and don't need one.
 */
object SampleDatabases {

  private val sqlAccess = Mutex()

  val driverDirectLock = Any()
  val openHelperDirectLock = Any()

  /** Serializes demo SQL and [reset] so handles are never closed mid-statement. */
  suspend fun <T> withSqlAccess(block: suspend () -> T): T = sqlAccess.withLock { block() }

  @Volatile private var driverConnection: SQLiteConnection? = null
  @Volatile private var driverRoom2Db: SampleRoom2Database? = null
  @Volatile private var driverRoom3Db: SampleRoom3Database? = null
  @Volatile private var directHelper: SupportSQLiteOpenHelper? = null
  @Volatile private var openHelperRoomDb: SampleRoom2Database? = null
  @Volatile private var sqlDelightDriver: AndroidSqliteDriver? = null

  fun driverConnection(context: Context): SQLiteConnection =
    synchronized(driverDirectLock) {
      driverConnection
        ?: SentrySQLiteDriver.create(BundledSQLiteDriver())
          .open(databaseFile(context, "driver_direct.db"))
          .also {
            it.execSQL(SqlStatements.CREATE_SONG) // one-time table setup, at open
            driverConnection = it
          }
    }

  fun driverRoom2Db(context: Context): SampleRoom2Database =
    synchronized(this) {
      driverRoom2Db
        ?: Room.databaseBuilder(
            context.applicationContext,
            SampleRoom2Database::class.java,
            "driver_room2.db",
          )
          .setDriver(SentrySQLiteDriver.create(BundledSQLiteDriver()))
          .setQueryCoroutineContext(Dispatchers.IO)
          .fallbackToDestructiveMigration(true)
          .build()
          .also { driverRoom2Db = it }
    }

  fun driverRoom3Db(context: Context): SampleRoom3Database =
    synchronized(this) {
      driverRoom3Db
        ?: Room3.databaseBuilder<SampleRoom3Database>(context.applicationContext, "driver_room3.db")
          .setDriver(SentrySQLiteDriver.create(BundledSQLiteDriver()))
          .setQueryCoroutineContext(Dispatchers.IO)
          .build()
          .also { driverRoom3Db = it }
    }

  fun directHelper(context: Context): SupportSQLiteOpenHelper =
    synchronized(openHelperDirectLock) {
      directHelper ?: buildDirectHelper(context).also { directHelper = it }
    }

  fun openHelperRoomDb(context: Context): SampleRoom2Database =
    synchronized(this) {
      openHelperRoomDb
        ?: Room.databaseBuilder(
            context.applicationContext,
            SampleRoom2Database::class.java,
            "openhelper_room.db",
          )
          .openHelperFactory { configuration ->
            SentrySupportSQLiteOpenHelper.create(
              FrameworkSQLiteOpenHelperFactory().create(configuration)
            )
          }
          .fallbackToDestructiveMigration(true)
          .build()
          .also { openHelperRoomDb = it }
    }

  fun sqlDelightDriver(context: Context): AndroidSqliteDriver =
    synchronized(this) {
      sqlDelightDriver
        ?: AndroidSqliteDriver(
            schema = SampleSQLDelightDatabase.Schema,
            context = context.applicationContext,
            name = "openhelper_sqldelight.db",
            factory =
              SupportSQLiteOpenHelper.Factory { configuration ->
                SentrySupportSQLiteOpenHelper.create(
                  FrameworkSQLiteOpenHelperFactory().create(configuration)
                )
              },
          )
          .also { sqlDelightDriver = it }
    }

  private fun buildDirectHelper(context: Context): SupportSQLiteOpenHelper {
    val configuration =
      SupportSQLiteOpenHelper.Configuration.builder(context.applicationContext)
        .name("openhelper_direct.db")
        .callback(
          object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
              db.execSQL(SqlStatements.CREATE_SONG)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
              Unit
          }
        )
        .build()
    return SentrySupportSQLiteOpenHelper.create(
      FrameworkSQLiteOpenHelperFactory().create(configuration)
    )
  }

  /** Opens every database on a background thread, forcing the one-time open + bootstrap to run. */
  fun warmUp(context: Context) {
    val appContext = context.applicationContext
    // Fire-and-forget: the warm-up outlives no particular screen, so a bare scope is fine here.
    CoroutineScope(Dispatchers.IO).launch {
      runCatching { driverConnection(appContext) }
      // primeWriter() + count() opens both Room pool connections (writer + reader), so the first
      // demo INSERT/SELECT reuses them instead of bootstrapping a connection inside its
      // transaction.
      runCatching { driverRoom2Db(appContext).songDao().also { it.primeWriter() }.count() }
      runCatching { driverRoom3Db(appContext).songDao().also { it.primeWriter() }.count() }
      runCatching { directHelper(appContext).writableDatabase }
      runCatching { openHelperRoomDb(appContext).songDao().also { it.primeWriter() }.count() }
      runCatching {
        SampleSQLDelightDatabase(sqlDelightDriver(appContext))
          .songQueries
          .countSongs()
          .executeAsOne()
      }
    }
  }

  /**
   * Closes the open handles, deletes every demo database file, then re-warms. Returns the number of
   * files cleared. Waits for any in-flight demo SQL (including [UiLoadActivity]) to finish first.
   */
  suspend fun reset(context: Context): Int = withSqlAccess {
    closeAll()
    val appContext = context.applicationContext
    val names =
      listOf(
        "driver_direct.db",
        "driver_room2.db",
        "driver_room3.db",
        "openhelper_direct.db",
        "openhelper_room.db",
        "openhelper_sqldelight.db",
      )
    val cleared = names.count { appContext.deleteDatabase(it) }
    warmUp(appContext)
    cleared
  }

  private fun closeAll() {
    synchronized(driverDirectLock) {
      driverConnection?.close()
      driverConnection = null
    }
    synchronized(openHelperDirectLock) {
      directHelper?.close()
      directHelper = null
    }
    synchronized(this) {
      driverRoom2Db?.close()
      driverRoom2Db = null
      driverRoom3Db?.close()
      driverRoom3Db = null
      openHelperRoomDb?.close()
      openHelperRoomDb = null
      sqlDelightDriver?.close()
      sqlDelightDriver = null
    }
  }

  private fun databaseFile(context: Context, name: String): String =
    context.applicationContext.getDatabasePath(name).also { it.parentFile?.mkdirs() }.absolutePath
}
