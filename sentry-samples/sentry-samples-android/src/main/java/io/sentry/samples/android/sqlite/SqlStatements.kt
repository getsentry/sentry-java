package io.sentry.samples.android.sqlite

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Rows inserted (and then consumed + processed) per demo when "heavy application-level work" is
 * enabled.
 */
private const val HEAVY_ROW_COUNT = 50

/**
 * Identifies a single SQLite demo: one of the two integrations crossed with the way it's used
 * (raw/direct, Room, or SQLDelight). Used to dispatch the same SQL from both trace styles.
 */
enum class SqlDemo {
  DRIVER_DIRECT,
  DRIVER_ROOM2,
  DRIVER_ROOM3,
  BRIDGE_DIRECT,
  BRIDGE_ROOM2,
  OPENHELPER_DIRECT,
  OPENHELPER_ROOM,
  OPENHELPER_SQLDELIGHT,
}

/**
 * Executable SQL and demo runners for the SQLite sample screens. The human-readable "SQL run"
 * summaries shown in the UI live in the per-demo [DisplayInfo] constants; keep those in lockstep
 * with the statements here.
 *
 * The actual SQL each demo runs is kept separate from how its trace is created so the two screens
 * can share it:
 * - [SQLiteActivity]: Wraps [execute] in a manual `Sentry.startTransaction(…)`.
 * - [UiLoadActivity]: Calls the same [execute] with no manual transaction, so the screen's auto
 *   `ui.load` transaction owns the resulting `db.sql.query` spans.
 *
 * All demos read the shared, already-warm handles from [SampleDatabases] and return a short status
 * line. [heavy] mirrors the screen's "heavy app-level work" toggle. When enabled, each demo also
 * batch inserts [HEAVY_ROW_COUNT] rows and consumes them with per-row [appWork].
 */
object SqlStatements {

  const val CREATE_SONG =
    "CREATE TABLE IF NOT EXISTS song(id INTEGER PRIMARY KEY, title TEXT, artist TEXT)"
  const val INSERT_SONG = "INSERT INTO song(title, artist) VALUES (?, ?)"
  const val SELECT_SONGS = "SELECT id, title, artist FROM song"
  const val COUNT_SONGS = "SELECT count(*) FROM song"

  /**
   * A single multi-row INSERT for [rowCount] songs, bound with [batchSongArgs]. One statement <>
   * one round-trip, which is the realistic way to add a known batch of rows, rather than a loop of
   * [rowCount] single-row inserts.
   */
  fun insertSongsBatch(rowCount: Int): String =
    "INSERT INTO song(title, artist) VALUES " + List(rowCount) { "(?, ?)" }.joinToString(", ")

  /** Flattened title/artist bind args for [insertSongsBatch]: "song 0", "artist 0", "song 1", … */
  fun batchSongArgs(rowCount: Int): Array<Any?> =
    Array(rowCount * 2) { i -> if (i % 2 == 0) "song ${i / 2}" else "artist ${i / 2}" }

  suspend fun execute(context: Context, demo: SqlDemo, heavy: Boolean): String =
    SampleDatabases.withSqlAccess {
      when (demo) {
        SqlDemo.DRIVER_DIRECT -> driverDirect(context, heavy)
        SqlDemo.DRIVER_ROOM2 -> driverWithRoom2(context, heavy)
        SqlDemo.DRIVER_ROOM3 -> driverWithRoom3(context, heavy)
        SqlDemo.BRIDGE_DIRECT -> bridgeDirect(context, heavy)
        SqlDemo.BRIDGE_ROOM2 -> bridgeWithRoom2(context, heavy)
        SqlDemo.OPENHELPER_DIRECT -> openHelperDirect(context, heavy)
        SqlDemo.OPENHELPER_ROOM -> openHelperWithRoom(context, heavy)
        SqlDemo.OPENHELPER_SQLDELIGHT -> openHelperWithSqlDelight(context, heavy)
      }
    }

  // --- 1. SentrySQLiteDriver, used directly -------------------------------------------------

  private fun driverDirect(context: Context, heavy: Boolean): String =
    synchronized(SampleDatabases.driverDirectLock) {
      val connection = SampleDatabases.driverConnection(context)
      insert(connection, "Mishima / Closing", "Philip Glass")
      insert(connection, "School of Velocity, op 299 no 1, ", "Carl Czerny")

      if (heavy) {
        // One multi-row INSERT for all HEAVY_ROWS rows, rather than a naive loop of single-row
        // inserts.
        connection.prepare(insertSongsBatch(HEAVY_ROW_COUNT)).use { statement ->
          var param = 1
          repeat(HEAVY_ROW_COUNT) { row ->
            statement.bindText(param++, "song $row")
            statement.bindText(param++, "artist $row")
          }
          statement.step()
        }

        connection.prepare(SELECT_SONGS).use { statement ->
          while (statement.step()) {
            // Consumption: pull each column across the JNI boundary into the ART heap.
            val row = "${statement.getLong(0)}:${statement.getText(1)}:${statement.getText(2)}"
            // Application work: e.g. per-row decryption.
            appWork(row)
          }
        }
      }
      "Driver (Direct): ${count(connection)} rows."
    }

  private fun insert(connection: SQLiteConnection, title: String, artist: String) {
    connection.prepare(INSERT_SONG).use { statement ->
      statement.bindText(1, title)
      statement.bindText(2, artist)
      statement.step()
    }
  }

  private fun count(connection: SQLiteConnection): Long =
    connection.prepare(COUNT_SONGS).use { statement ->
      if (statement.step()) statement.getLong(0) else 0
    }

  // --- 1b. SupportSQLiteDriver bridge (helper + driver both wrapped; SDK skips driver wrap) --

  private fun bridgeDirect(context: Context, heavy: Boolean): String =
    synchronized(SampleDatabases.bridgeDirectLock) {
      val connection = SampleDatabases.bridgeConnection(context)
      insert(connection, "Mishima / Closing", "Philip Glass")
      insert(connection, "School of Velocity, op 299 no 1, ", "Carl Czerny")

      if (heavy) {
        connection.prepare(insertSongsBatch(HEAVY_ROW_COUNT)).use { statement ->
          var param = 1
          repeat(HEAVY_ROW_COUNT) { row ->
            statement.bindText(param++, "song $row")
            statement.bindText(param++, "artist $row")
          }
          statement.step()
        }

        connection.prepare(SELECT_SONGS).use { statement ->
          while (statement.step()) {
            val row = "${statement.getLong(0)}:${statement.getText(1)}:${statement.getText(2)}"
            appWork(row)
          }
        }
      }
      "Bridge (Direct): ${count(connection)} rows."
    }

  private suspend fun bridgeWithRoom2(context: Context, heavy: Boolean): String =
    roomDemo(SampleDatabases.bridgeRoom2Db(context).songDao(), "Bridge (Room 2)", heavy)

  // --- 2. SentrySQLiteDriver, used through Room 2.7+ ----------------------------------------

  private suspend fun driverWithRoom2(context: Context, heavy: Boolean): String =
    roomDemo(SampleDatabases.driverRoom2Db(context).songDao(), "Driver (Room 2)", heavy)

  /**
   * Shared Room 2 demo so the driver and open-helper paths run *identical* SQL. The only difference
   * is how each integration instruments it: the driver spans every read, while the open helper's
   * Room reads go via `moveToNext()` and emit no span, so only the INSERTs are spanned.
   */
  private suspend fun roomDemo(dao: SongDao, label: String, heavy: Boolean): String {
    dao.insert(SongEntity(title = "Spiders (Kidsmoke)", artist = "Wilco"))
    if (heavy) {
      // Batch insert: one insertAll() runs all rows in a single transaction, vs. a per-row loop.
      dao.insertAll(List(HEAVY_ROW_COUNT) { SongEntity(title = "song $it", artist = "artist $it") })
      dao.getAll().forEach { appWork("${it.id}:${it.title}:${it.artist}") }
    }
    return "$label: ${dao.count()} rows."
  }

  // --- 2b. SentrySQLiteDriver, used through Room 3.0+ (androidx.room3) -----------------------

  private suspend fun driverWithRoom3(context: Context, heavy: Boolean): String {
    val dao = SampleDatabases.driverRoom3Db(context).songDao()
    dao.insert(SongEntity3(title = "What's Up", artist = "4 Non Blondes"))
    if (heavy) {
      // Batch insert: one insertAll() runs all rows in a single transaction, vs. a naive per-row
      // loop.
      dao.insertAll(
        List(HEAVY_ROW_COUNT) { SongEntity3(title = "song $it", artist = "artist $it") }
      )
      dao.getAll().forEach { appWork("${it.id}:${it.title}:${it.artist}") }
    }
    return "Driver (Room 3): ${dao.count()} rows."
  }

  // --- 3. SentrySupportSQLiteOpenHelper, used directly --------------------------------------

  private fun openHelperDirect(context: Context, heavy: Boolean): String =
    synchronized(SampleDatabases.openHelperDirectLock) {
      // Runs the *same* SQL as driverDirect(), so the only difference you see in the Sentry UI is
      // how each integration instruments identical statements.
      val db = SampleDatabases.directHelper(context).writableDatabase
      db.execSQL(INSERT_SONG, arrayOf("Mishima / Closing", "Philip Glass"))
      db.execSQL(INSERT_SONG, arrayOf("School of Velocity, op 299 no 1, ", "Carl Czerny"))
      if (heavy) {
        // One multi-row INSERT for all HEAVY_ROWS rows, rather than a naive loop of single-row
        // inserts.
        db.execSQL(insertSongsBatch(HEAVY_ROW_COUNT), batchSongArgs(HEAVY_ROW_COUNT))
        db.query(SELECT_SONGS).use { cursor ->
          while (cursor.moveToNext()) {
            // Consumption: read each column out of the cursor window.
            val row = "${cursor.getLong(0)}:${cursor.getString(1)}:${cursor.getString(2)}"
            // Application work: e.g. per-row decryption.
            appWork(row)
          }
        }
      }
      "OpenHelper (Direct): ${querySongCount(db)} rows."
    }

  /**
   * Runs the shared `SELECT count(*)` through the open helper and returns the value, read the
   * normal way: moveToFirst() + getInt(). These are delegated straight to the underlying cursor
   * (the open helper only instruments getCount()/onMove()/fillWindow()), so this read produces no
   * `db.sql.query` span — the same as a real app reading a scalar count.
   */
  private fun querySongCount(db: SupportSQLiteDatabase): Int =
    db.query(COUNT_SONGS).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    }

  // --- 4. SentrySupportSQLiteOpenHelper, used through Room ----------------------------------

  // Runs the same [roomDemo] SQL as the driver path; only the instrumentation differs.
  private suspend fun openHelperWithRoom(context: Context, heavy: Boolean): String =
    roomDemo(SampleDatabases.openHelperRoomDb(context).songDao(), "OpenHelper (Room)", heavy)

  // --- 5. SentrySupportSQLiteOpenHelper, used through SQLDelight ----------------------------

  private fun openHelperWithSqlDelight(context: Context, heavy: Boolean): String {
    val database = SampleSQLDelightDatabase(SampleDatabases.sqlDelightDriver(context))
    database.songQueries.insertSong("Nightcall", "Kavinsky")
    if (heavy) {
      // Wrap the batch in one transaction, vs. each insertSong() naively committing on its own.
      database.transaction {
        repeat(HEAVY_ROW_COUNT) { database.songQueries.insertSong("song $it", "artist $it") }
      }
      database.songQueries.selectAll().executeAsList().forEach {
        appWork("${it.id}:${it.title}:${it.artist}")
      }
    }
    // SQLDelight reads its cursor only via moveToNext(), which is delegated past the wrapper, so
    // this count read produces no span.
    val count = database.songQueries.countSongs().executeAsOne()
    return "OpenHelper (SQLDelight): $count rows."
  }

  /**
   * Simulates per-row application-level work (e.g. decrypting a column) on consumed results. This
   * is deliberately CPU-heavy and unrelated to the SQLite engine.
   */
  private fun appWork(value: String) {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    var bytes = value.toByteArray()
    repeat(500) { bytes = digest.digest(bytes) }
  }
}
