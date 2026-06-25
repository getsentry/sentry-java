package io.sentry.samples.android.sqlite

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room3.Room as Room3
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.driver.SupportSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.sentry.android.sqlite.SentrySupportSQLiteOpenHelper
import io.sentry.samples.android.BuildConfig
import io.sentry.samples.android.sqlite.SampleDatabases.driverDirectLock
import io.sentry.samples.android.sqlite.SampleDatabases.openHelperDirectLock
import io.sentry.samples.android.sqlite.SampleDatabases.reset
import io.sentry.samples.android.sqlite.SampleDatabases.warmUp
import io.sentry.sqlite.SentrySQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

  private const val TAG = "SampleDatabases"

  /** Non-empty when one or more warm-up steps failed; shown on [SQLiteActivity]. */
  @Volatile
  var warmUpErrors: String = ""
    private set

  @Volatile private var warmUpComplete = false
  @Volatile private var warmUpGeneration = 0
  @Volatile private var warmUpJob: Job? = null

  fun isWarmUpComplete(): Boolean = warmUpComplete

  /** Blocks until the in-flight [warmUp] job (if any) finishes. */
  suspend fun awaitWarmUp() {
    warmUpJob?.join()
  }

  private val sqlAccess = Mutex()

  val driverDirectLock = Any()
  val bridgeDirectLock = Any()
  val openHelperDirectLock = Any()

  /** Serializes demo SQL and [reset] so handles are never closed mid-statement. */
  suspend fun <T> withSqlAccess(block: suspend () -> T): T = sqlAccess.withLock { block() }

  @Volatile private var driverConnection: SQLiteConnection? = null
  @Volatile private var bridgeConnection: SQLiteConnection? = null
  @Volatile private var driverRoom2Db: SampleRoom2Database? = null
  @Volatile private var bridgeRoom2Db: SampleRoom2Database? = null
  @Volatile private var driverRoom3Db: SampleRoom3Database? = null
  @Volatile private var directHelper: SupportSQLiteOpenHelper? = null
  @Volatile private var bridgeDirectHelper: SupportSQLiteOpenHelper? = null
  @Volatile private var openHelperRoomDb: SampleRoom2Database? = null
  @Volatile private var sqlDelightDriver: AndroidSqliteDriver? = null

  fun driverConnection(context: Context): SQLiteConnection =
    synchronized(driverDirectLock) {
      driverConnection
        ?: wrapDriver(BundledSQLiteDriver()).open(databaseFile(context, "driver_direct.db")).also {
          it.execSQL(SqlStatements.CREATE_SONG) // one-time table setup, at open
          driverConnection = it
        }
    }

  /**
   * The Room 2.7+ duplicate-span scenario: a Sentry-wrapped open helper bridged to
   * [SupportSQLiteDriver], then passed to [SentrySQLiteDriver.create] (which no-ops on the bridge).
   */
  fun bridgeConnection(context: Context): SQLiteConnection =
    synchronized(bridgeDirectLock) {
      bridgeConnection
        ?: run {
          // SupportSQLiteDriver.open() requires fileName to match the helper's databaseName();
          // use the absolute path Room and the direct driver path both pass to open().
          val dbPath = databaseFile(context, "bridge_direct.db")
          wrapDriver(SupportSQLiteDriver(buildBridgeDirectHelper(context, dbPath)))
            .open(dbPath)
            .also {
              it.execSQL(SqlStatements.CREATE_SONG)
              bridgeConnection = it
            }
        }
    }

  fun bridgeRoom2Db(context: Context): SampleRoom2Database =
    synchronized(this) {
      bridgeRoom2Db
        ?: Room.databaseBuilder(
            context.applicationContext,
            SampleRoom2Database::class.java,
            "bridge_room2.db",
          )
          .setDriver(
            wrapDriver(SupportSQLiteDriver(buildBridgeRoom2Helper(context.applicationContext)))
          )
          .setQueryCoroutineContext(Dispatchers.IO)
          .fallbackToDestructiveMigration(true)
          .build()
          .also { bridgeRoom2Db = it }
    }

  fun driverRoom2Db(context: Context): SampleRoom2Database =
    synchronized(this) {
      driverRoom2Db
        ?: Room.databaseBuilder(
            context.applicationContext,
            SampleRoom2Database::class.java,
            "driver_room2.db",
          )
          .setDriver(wrapDriver(BundledSQLiteDriver()))
          .setQueryCoroutineContext(Dispatchers.IO)
          .fallbackToDestructiveMigration(true)
          .build()
          .also { driverRoom2Db = it }
    }

  fun driverRoom3Db(context: Context): SampleRoom3Database =
    synchronized(this) {
      driverRoom3Db
        ?: Room3.databaseBuilder<SampleRoom3Database>(context.applicationContext, "driver_room3.db")
          .setDriver(wrapDriver(BundledSQLiteDriver()))
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
            wrapOpenHelper(FrameworkSQLiteOpenHelperFactory().create(configuration))
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
                wrapOpenHelper(FrameworkSQLiteOpenHelperFactory().create(configuration))
              },
          )
          .also { sqlDelightDriver = it }
    }

  private fun buildDirectHelper(context: Context): SupportSQLiteOpenHelper =
    buildSentryHelper(context, "openhelper_direct.db").also { directHelper = it }

  private fun buildBridgeDirectHelper(context: Context, dbPath: String): SupportSQLiteOpenHelper =
    buildSentryHelper(context, dbPath).also { bridgeDirectHelper = it }

  /**
   * Open helper for the Bridge + Room 2 stack. Must not create tables in [onCreate] — Room owns the
   * schema when [setDriver] is used. Room also passes [SupportSQLiteOpenHelper.databaseName] (the
   * short name below), not an absolute path, to [SupportSQLiteDriver.open].
   *
   * The callback version must be 1 (FrameworkSQLiteOpenHelper rejects &lt; 1). That sets `PRAGMA
   * user_version = 1` before Room opens, so Room would skip [onCreate] and validate the empty file
   * as pre-packaged → "invalid schema". [onOpen] clears user_version back to 0 until
   * [ROOM_MASTER_TABLE] exists.
   */
  private fun buildBridgeRoom2Helper(context: Context): SupportSQLiteOpenHelper {
    val configuration =
      SupportSQLiteOpenHelper.Configuration.builder(context.applicationContext)
        .name("bridge_room2.db")
        .callback(
          object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
              Unit

            override fun onOpen(db: SupportSQLiteDatabase) {
              if (!db.hasRoomMasterTable()) {
                db.execSQL("PRAGMA user_version = 0")
              }
            }
          }
        )
        .build()
    return wrapOpenHelper(FrameworkSQLiteOpenHelperFactory().create(configuration))
  }

  private fun buildSentryHelper(context: Context, dbName: String): SupportSQLiteOpenHelper {
    val configuration =
      SupportSQLiteOpenHelper.Configuration.builder(context.applicationContext)
        .name(dbName)
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
    return wrapOpenHelper(FrameworkSQLiteOpenHelperFactory().create(configuration))
  }

  private fun wrapDriver(driver: SQLiteDriver): SQLiteDriver =
    if (BuildConfig.USE_SAGP) driver else SentrySQLiteDriver.create(driver)

  private fun wrapOpenHelper(delegate: SupportSQLiteOpenHelper): SupportSQLiteOpenHelper =
    if (BuildConfig.USE_SAGP) delegate else SentrySupportSQLiteOpenHelper.create(delegate)

  /** Opens every database on a background thread, forcing the one-time open + bootstrap to run. */
  fun warmUp(context: Context) {
    val appContext = context.applicationContext
    val generation = ++warmUpGeneration
    warmUpComplete = false
    warmUpErrors = ""
    Log.i(TAG, "Warm-up starting (USE_SAGP=${BuildConfig.USE_SAGP})")
    // Fire-and-forget: the warm-up outlives no particular screen, so a bare scope is fine here.
    warmUpJob =
      CoroutineScope(Dispatchers.IO).launch {
        val failures = mutableListOf<String>()
        runWarmUpStep("driver direct", failures) { driverConnection(appContext) }
        runWarmUpStep("bridge direct", failures) { bridgeConnection(appContext) }
        // primeWriter() + count() opens both Room pool connections (writer + reader), so the first
        // demo INSERT/SELECT reuses them instead of bootstrapping a connection inside its
        // transaction.
        runWarmUpStep("driver Room 2", failures) {
          driverRoom2Db(appContext).songDao().also { it.primeWriter() }.count()
        }
        runWarmUpStep("bridge Room 2", failures) {
          bridgeRoom2Db(appContext).songDao().also { it.primeWriter() }.count()
        }
        runWarmUpStep("driver Room 3", failures) {
          driverRoom3Db(appContext).songDao().also { it.primeWriter() }.count()
        }
        runWarmUpStep("open helper direct", failures) { directHelper(appContext).writableDatabase }
        runWarmUpStep("open helper Room", failures) {
          openHelperRoomDb(appContext).songDao().also { it.primeWriter() }.count()
        }
        runWarmUpStep("SQLDelight", failures) {
          SampleSQLDelightDatabase(sqlDelightDriver(appContext))
            .songQueries
            .countSongs()
            .executeAsOne()
        }
        if (generation == warmUpGeneration) {
          warmUpErrors = failures.joinToString("\n") { "Warm-up failed: $it" }
          warmUpComplete = true
        }
      }
  }

  private inline fun runWarmUpStep(step: String, failures: MutableList<String>, block: () -> Unit) {
    try {
      block()
    } catch (t: Throwable) {
      Log.e(TAG, "Warm-up failed: $step", t)
      failures.add("$step: ${t.message ?: t.javaClass.simpleName}")
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
        "bridge_direct.db",
        "driver_room2.db",
        "bridge_room2.db",
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
    synchronized(bridgeDirectLock) {
      bridgeConnection?.close()
      bridgeConnection = null
      bridgeDirectHelper?.close()
      bridgeDirectHelper = null
    }
    synchronized(openHelperDirectLock) {
      directHelper?.close()
      directHelper = null
    }
    synchronized(this) {
      driverRoom2Db?.close()
      driverRoom2Db = null
      bridgeRoom2Db?.close()
      bridgeRoom2Db = null
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

  private fun SupportSQLiteDatabase.hasRoomMasterTable(): Boolean =
    query("SELECT 1 FROM sqlite_master WHERE name = '$ROOM_MASTER_TABLE' LIMIT 1").use {
      it.moveToFirst()
    }
}

private const val ROOM_MASTER_TABLE = "room_master_table"
