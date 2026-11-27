package io.sentry.sqlite

/** [DB_SYSTEM_KEY][io.sentry.SpanDataConvention.DB_SYSTEM_KEY] value for in-memory databases. */
internal const val DB_SYSTEM_IN_MEMORY = "in-memory"

/** [DB_SYSTEM_KEY][io.sentry.SpanDataConvention.DB_SYSTEM_KEY] value for SQLite databases. */
internal const val DB_SYSTEM_SQLITE = "sqlite"

/** Maximum length of the sanitized query text on a span. */
internal const val MAX_QUERY_TEXT_LENGTH = 8 * 1024

/**
 * Sentinel file name that [SQLiteDriver.open][androidx.sqlite.SQLiteDriver.open] interprets as an
 * in-memory database (see docs
 * [here](https://developer.android.com/reference/androidx/sqlite/driver/AndroidSQLiteDriver)).
 */
private const val IN_MEMORY_DB_FILENAME = ":memory:"

/** Path separators matching [File.separatorChar][java.io.File.separatorChar]. */
private val FILE_NAME_PATH_SEPARATORS = charArrayOf('/', '\\')

/**
 * Start of the query and fragment of a URI filename, which can hold parameters such as an
 * encryption key.
 */
private val URI_PARAMETER_STARTS = charArrayOf('?', '#')

internal data class DbMetadata(val name: String?, val system: String)

/**
 * Returns metadata based on the [fileName] argument passed to
 * [SQLiteDriver.open][androidx.sqlite.SQLiteDriver.open].
 */
internal fun dbMetadataFromFileName(fileName: String): DbMetadata {
  if (fileName == IN_MEMORY_DB_FILENAME) {
    return DbMetadata(name = null, system = DB_SYSTEM_IN_MEMORY)
  }

  val parametersStart = fileName.indexOfAny(URI_PARAMETER_STARTS)
  val path = if (parametersStart >= 0) fileName.substring(0, parametersStart) else fileName
  val trimmed = path.trimEnd { it in FILE_NAME_PATH_SEPARATORS }
  if (trimmed.isEmpty()) {
    return DbMetadata(name = null, system = DB_SYSTEM_SQLITE)
  }

  val index = trimmed.lastIndexOfAny(FILE_NAME_PATH_SEPARATORS)
  val basename = if (index >= 0) trimmed.substring(index + 1) else trimmed
  return DbMetadata(name = basename.ifEmpty { null }, system = DB_SYSTEM_SQLITE)
}
