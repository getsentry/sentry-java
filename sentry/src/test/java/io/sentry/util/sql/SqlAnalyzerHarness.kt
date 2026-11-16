package io.sentry.util.sql

import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Every dialect the robustness tests run under: `CONSERVATIVE` plus each dialect-table system. */
internal val ALL_DIALECTS: Map<String, SqlDialect> =
  linkedMapOf("CONSERVATIVE" to SqlDialect.CONSERVATIVE) +
    listOf(
        "postgresql",
        "mysql",
        "mariadb",
        "sqlite",
        "in-memory",
        "sqlserver",
        "oracle",
        "h2",
        "hsqldb",
        "derby",
      )
      .associateWith { SqlDialect.fromDbSystem(it) }

internal fun SqlQuery.fields(): Map<String, String?> =
  linkedMapOf(
    "text" to queryText,
    "summary" to querySummary,
    "storedProcedure" to storedProcedureName,
  )

/**
 * Asserts what every robustness input must give: no internal error, and therefore a text. Status
 * `ERROR` means [SqlQueryAnalyzer.analyze] caught an exception (R5), which no input may cause; and
 * text is `null` only after `ERROR` (D27). Together the two imply D27's invariant for non-null
 * input (plan 01, step 11d). Its other half, that `ERROR` comes with `null` text, has no test: no
 * input may reach `ERROR`, and forcing one would need a production seam.
 */
internal fun assertNoInternalError(query: SqlQuery, describe: () -> String) {
  if (query.status == SqlQuery.Status.ERROR) {
    assertWithMessage("internal error (R5); %s", describe())
      .that(query.status)
      .isNotEqualTo(SqlQuery.Status.ERROR)
  }
  if (query.queryText == null) {
    assertWithMessage(
        "text null with status %s (D27: null only after ERROR); %s",
        query.status,
        describe(),
      )
      .that(query.queryText)
      .isNotNull()
  }
}

/** Every substring of [value] that is [FRAGMENT_LENGTH] characters long. */
internal fun fragments(value: String): Set<String> =
  (0..value.length - FRAGMENT_LENGTH).map { value.substring(it, it + FRAGMENT_LENGTH) }.toSet()

/** Shortest secret fragment that counts as a leak (plan 01, step 6). */
internal const val FRAGMENT_LENGTH = 4

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
 * [timeoutMillis] instead of stalling the build.
 */
internal class AnalyzerWorker : AutoCloseable {
  private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(null, runnable, "sql-analyzer-worker", STACK_SIZE_BYTES).apply { isDaemon = true }
  }

  fun analyze(
    sql: String,
    dialect: SqlDialect,
    maxQueryTextLength: Int,
    timeoutMillis: Long,
    describe: () -> String,
  ): SqlQuery {
    val future =
      executor.submit(Callable { SqlQueryAnalyzer.analyze(sql, dialect, maxQueryTextLength) })
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
