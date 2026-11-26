package io.sentry.util.sql

import com.google.common.truth.Truth.assertWithMessage
import io.sentry.JsonObjectReader
import io.sentry.vendor.gson.stream.JsonToken
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * One `db.system.name` per dialect option of the vendored `SqlDialect`: `null` reads double quotes
 * as literals, `postgresql` as identifiers. The robustness tests run under both.
 */
internal val DIALECTS: List<String?> = listOf(null, "postgresql")

internal fun SqlQuery.fields(): Map<String, String?> =
  linkedMapOf(
    "text" to queryText,
    "summary" to querySummary,
    "storedProcedure" to storedProcedureName,
  )

/**
 * What every robustness input must give: a text. Non-null input up to 1 MiB has `null` text only
 * after an internal error (R5), which no input may cause.
 */
internal fun assertHasText(query: SqlQuery, describe: () -> String) {
  if (query.queryText == null) {
    assertWithMessage("no text, so the analyzer failed internally (R5); %s", describe())
      .that(query.queryText)
      .isNotNull()
  }
}

internal fun assertWithinLimit(query: SqlQuery, limit: Int, describe: () -> String) {
  assertWithMessage("text longer than the limit; %s", describe())
    .that(query.queryText?.length ?: 0)
    .isAtMost(limit)
}

/**
 * No output splits a surrogate pair. A lone surrogate in the input passes through the grammar like
 * any other character, so inputs that have one are not checked.
 */
internal fun assertNoLoneSurrogate(input: String, query: SqlQuery, describe: () -> String) {
  if (hasLoneSurrogate(input)) return
  for ((field, value) in query.fields()) {
    if (value != null && hasLoneSurrogate(value)) {
      assertWithMessage("lone surrogate in %s; %s", field, describe())
        .that(escape(value))
        .isEqualTo("no lone surrogate")
    }
  }
}

private fun hasLoneSurrogate(value: String): Boolean {
  var i = 0
  while (i < value.length) {
    val c = value[i]
    if (c.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate()) i += 2
    else if (c.isSurrogate()) return true else i++
  }
  return false
}

/** Escapes control characters and surrogates so a failing input can be read and copied. */
internal fun escape(value: String, maxLength: Int = 4096): String {
  val out = StringBuilder()
  for (c in value.take(maxLength)) {
    when {
      c == '\\' -> out.append("\\\\")
      c == '\n' -> out.append("\\n")
      c == '\r' -> out.append("\\r")
      c == '\t' -> out.append("\\t")
      c < ' ' || c.isSurrogate() || c.code in 0x7f..0x9f ->
        out.append("\\u").append(String.format("%04x", c.code))
      else -> out.append(c)
    }
  }
  if (value.length > maxLength) out.append("… (${value.length} chars)")
  return out.toString()
}

/**
 * Runs [SqlQueryAnalyzer.analyze] on a daemon thread with a fixed stack, so that a recursion depth
 * that would overflow a typical Android or JVM worker thread fails here, and a hang fails after
 * `timeoutMillis` instead of stalling the build.
 */
internal class AnalyzerWorker : AutoCloseable {
  private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(null, runnable, "sql-analyzer-worker", STACK_SIZE_BYTES).apply { isDaemon = true }
  }

  fun analyze(
    sql: String,
    dbSystemName: String?,
    maxQueryTextLength: Int,
    timeoutMillis: Long,
    describe: () -> String,
  ): SqlQuery {
    val future =
      executor.submit(Callable { SqlQueryAnalyzer.analyze(sql, dbSystemName, maxQueryTextLength) })
    try {
      return future.get(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
      future.cancel(true)
      throw AssertionError("analyze did not complete within $timeoutMillis ms: ${describe()}")
    } catch (e: ExecutionException) {
      throw AssertionError("analyze threw ${e.cause}: ${describe()}", e.cause)
    }
  }

  override fun close() {
    executor.shutdownNow()
  }

  companion object {
    /** Below the default for Android background threads and HotSpot (both about 1 MiB). */
    const val STACK_SIZE_BYTES = 512L * 1024
  }
}

/**
 * A case of `sql/analyzer-cases.json`: an input with the `db.system.name` and limit it is analyzed
 * with, and the secrets it declares. The file has no expected outputs (plan 08, Tests).
 */
class SqlCase(
  val id: String,
  val dbSystemName: String?,
  val input: String,
  val maxQueryTextLength: Int,
  val secrets: List<String>,
) {
  override fun toString(): String = id
}

internal object SqlCorpus {
  private const val RESOURCE = "sql/analyzer-cases.json"
  private val SOURCE = Regex("sentry|otel-java:\\w+#.+|otel-dotnet:[^#]+#.+")
  private val REQUIRED_KEYS = setOf("id", "dialect", "input", "source")

  /** Used when a case sets no limit: larger than any input. */
  private const val UNLIMITED = Int.MAX_VALUE

  val cases: List<SqlCase> by lazy { load() }

  /**
   * Rejects anything that would silently weaken a case: unknown or missing keys, unknown dialects.
   */
  private fun load(): List<SqlCase> {
    val stream =
      checkNotNull(SqlCorpus::class.java.classLoader.getResourceAsStream(RESOURCE)) {
        "$RESOURCE not found"
      }
    val cases = mutableListOf<SqlCase>()
    JsonObjectReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
      reader.beginObject()
      while (reader.hasNext()) {
        when (val key = reader.nextName()) {
          // The license header of the adapted upstream cases: JSON has no comments.
          "attribution" -> check(readStrings(reader, key).isNotEmpty())
          "cases" -> {
            reader.beginArray()
            while (reader.hasNext()) {
              val case = readCase(reader)
              check(cases.none { it.id == case.id }) { "$RESOURCE: duplicate id '${case.id}'" }
              cases += case
            }
            reader.endArray()
          }
          else -> error("$RESOURCE: unknown top-level key '$key'")
        }
      }
      reader.endObject()
    }
    check(cases.isNotEmpty()) { "$RESOURCE has no cases" }
    return cases
  }

  private fun readCase(reader: JsonObjectReader): SqlCase {
    var id: String? = null
    var dbSystemName: String? = null
    var input: String? = null
    var maxQueryTextLength = UNLIMITED
    var secrets = emptyList<String>()
    val seen = mutableSetOf<String>()
    reader.beginObject()
    while (reader.hasNext()) {
      val key = reader.nextName()
      check(seen.add(key)) { "$RESOURCE: duplicate key '$key' in case '$id'" }
      when (key) {
        "id" -> id = readString(reader, key)
        "dialect" -> dbSystemName = toDbSystemName(readString(reader, key))
        "input" -> input = readString(reader, key)
        "maxQueryTextLength" -> {
          check(reader.peek() == JsonToken.NUMBER) { "'$key' must be an integer" }
          maxQueryTextLength = reader.nextInt()
          check(maxQueryTextLength > 0) { "'$key' must be positive" }
        }
        "secrets" -> secrets = readStrings(reader, key)
        "source" -> check(SOURCE.matches(readString(reader, key))) { "case '$id': bad source" }
        else -> error("$RESOURCE: unknown key '$key' in case '$id'")
      }
    }
    reader.endObject()
    check(seen.containsAll(REQUIRED_KEYS)) {
      "$RESOURCE: case '$id' misses ${REQUIRED_KEYS - seen}"
    }
    return SqlCase(id!!, dbSystemName, input!!, maxQueryTextLength, secrets)
  }

  /**
   * The file names dialects by the `db.system` the integrations report; the analyzer takes the
   * semconv `db.system.name`, as [SqlSpanData] passes it. `CONSERVATIVE` is an unknown database. A
   * value with no mapping is a typo, not an unknown database.
   */
  private fun toDbSystemName(dialect: String): String? {
    if (dialect == "CONSERVATIVE") return null
    return checkNotNull(SqlSpanData.toDbSystemName(dialect)) { "unknown dialect '$dialect'" }
  }

  private fun readString(reader: JsonObjectReader, key: String): String {
    check(reader.peek() == JsonToken.STRING) { "'$key' must be a string" }
    return reader.nextString()
  }

  private fun readStrings(reader: JsonObjectReader, key: String): List<String> {
    val values = mutableListOf<String>()
    reader.beginArray()
    while (reader.hasNext()) values += readString(reader, key)
    reader.endArray()
    return values
  }
}
