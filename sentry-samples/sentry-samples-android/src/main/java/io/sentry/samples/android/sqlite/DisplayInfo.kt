package io.sentry.samples.android.sqlite

/**
 * Display text for each "SQL run" summary shown in the [SQLiteActivity] screen UI. Documentation
 * only / never executed. The real statements live in [SqlStatements].
 */
internal data class DisplayInfo(val sql: String, val sqlHeavy: String = sql)

internal val DRIVER_DIRECT =
  DisplayInfo(
    sql =
      """
      CREATE TABLE IF NOT EXISTS song(…)
      INSERT INTO song(title, artist) VALUES (?, ?)
      SELECT count(*) FROM song
      """
        .trimIndent(),
    sqlHeavy =
      """
      CREATE TABLE IF NOT EXISTS song(…)
      INSERT INTO song(title, artist) VALUES (?, ?)
      INSERT INTO song(title, artist) VALUES (?, ?), (?, ?), … (?, ?)
      SELECT id, title, artist FROM song
      SELECT count(*) FROM song
      -- then, per row: appWork() = 500x SHA-256, in the app (not in any span)
      """
        .trimIndent(),
  )

internal val DRIVER_ROOM2 =
  DisplayInfo(
    sql =
      """
      INSERT OR ABORT INTO `song` (…) VALUES (nullif(?, 0), ?, ?)
      SELECT count(*) FROM song
      """
        .trimIndent(),
    sqlHeavy =
      """
      INSERT OR ABORT INTO `song` (…) VALUES (nullif(?, 0), ?, ?)
      SELECT * FROM song
      SELECT count(*) FROM song
      -- then, per row: appWork() = 500x SHA-256, outside the step()-timed spans
      """
        .trimIndent(),
  )

// Room 3 issues the same statements as Room 2 (see SqlStatements.driverWithRoom3).
internal val DRIVER_ROOM3 = DRIVER_ROOM2

internal val OPENHELPER_DIRECT =
  DisplayInfo(
    sql =
      """
      CREATE TABLE IF NOT EXISTS song(…)
      INSERT INTO song(title, artist) VALUES (?, ?)
      SELECT count(*) FROM song
      """
        .trimIndent(),
    sqlHeavy =
      """
      CREATE TABLE IF NOT EXISTS song(…)
      INSERT INTO song(title, artist) VALUES (?, ?)
      INSERT INTO song(title, artist) VALUES (?, ?), (?, ?), … (?, ?)
      SELECT id, title, artist FROM song
      SELECT count(*) FROM song
      -- then, per row: appWork() = 500x SHA-256, in the app
      """
        .trimIndent(),
  )

internal val OPENHELPER_ROOM =
  DisplayInfo(
    sql =
      """
      INSERT OR ABORT INTO `song` (…) VALUES (nullif(?, 0), ?, ?)
      SELECT count(*) FROM song
      """
        .trimIndent(),
    sqlHeavy =
      """
      INSERT OR ABORT INTO `song` (…) VALUES (nullif(?, 0), ?, ?)
      SELECT * FROM song
      SELECT count(*) FROM song
      -- then, per row: appWork() = 500x SHA-256, in the app
      """
        .trimIndent(),
  )

internal val OPENHELPER_SQLDELIGHT =
  DisplayInfo(
    sql =
      """
      INSERT INTO song(title, artist) VALUES (?, ?)
      SELECT count(*) FROM song
      """
        .trimIndent(),
    sqlHeavy =
      """
      INSERT INTO song(title, artist) VALUES (?, ?)
      SELECT * FROM song
      SELECT count(*) FROM song
      -- then, per row: appWork() = 500x SHA-256, in the app
      """
        .trimIndent(),
  )
