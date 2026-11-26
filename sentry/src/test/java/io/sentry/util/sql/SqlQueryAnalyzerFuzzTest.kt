// Random UTF-16 inputs adapted from OpenTelemetry Java Instrumentation
// (SqlQueryAnalyzerTest#randomBytesDontCauseExceptionsOrTimeouts).
// Copyright The OpenTelemetry Authors.
// Licensed under the Apache License, Version 2.0.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/2acd9a5f1153e725a3b80e9b90ad55b385576fe8/instrumentation-api-incubator/src/test/java/io/opentelemetry/instrumentation/api/incubator/semconv/db/SqlQueryAnalyzerTest.java
package io.sentry.util.sql

import java.util.Random
import org.junit.Test

/**
 * Owns stability for generated input: [SqlQueryAnalyzer.analyze] must not throw or fail internally,
 * must respect `maxQueryTextLength`, split no surrogate pair, and finish each input within
 * [TIME_BOUND_MILLIS] (plan 01, step 6). Outputs and leaks are not asserted (plan 08, Tests).
 *
 * Each test runs a fixed seed and a run seed. The run seed is random unless the
 * `sentry.sql.fuzz.seed` system property sets it; every failure message prints the seed and the
 * command that reproduces it.
 */
class SqlQueryAnalyzerFuzzTest {

  /**
   * Token soup over keywords, operators, markers and every quote and comment construct, closed or
   * not. Half the inputs drop separators at random, which exercises boundary lexing (`x-1`,
   * `ELSE'x'`, `1--1`).
   */
  @Test
  fun `token inputs never fail, exceed the limit or the time bound`() {
    run(TOKEN_INPUTS) { random ->
      TokenInputGenerator(random, glue = random.nextBoolean()).generate()
    }
  }

  @Test
  fun `random UTF-16 inputs never fail, exceed the limit or the time bound`() {
    run(UTF16_INPUTS) { random ->
      val keyword = if (random.nextInt(4) != 0) pick(random, LEADING_KEYWORDS) + " " else ""
      keyword + String(CharArray(UTF16_LENGTH) { random.nextInt(0x10000).toChar() })
    }
  }

  private fun run(count: Int, generate: (Random) -> String) {
    AnalyzerWorker().use { worker ->
      warmUp(worker)
      for (seed in seeds()) {
        val random = Random(seed)
        repeat(count) { index ->
          val dbSystemName = pick(random, DIALECTS)
          val limit = pickLimit(random)
          val input = generate(random)
          val describe = {
            "seed=$seed index=$index dbSystemName=$dbSystemName maxQueryTextLength=$limit " +
              "input=\"${escape(input)}\"\nReproduce: ./gradlew :sentry:test " +
              "--tests '${SqlQueryAnalyzerFuzzTest::class.java.name}' -D$SEED_PROPERTY=$seed"
          }
          val query = worker.analyze(input, dbSystemName, limit, TIME_BOUND_MILLIS, describe)

          assertHasText(query, describe)
          assertWithinLimit(query, limit, describe)
          assertNoLoneSurrogate(input, query, describe)
        }
      }
    }
  }

  /** Untimed runs so class loading and JIT compilation don't count against the first input. */
  private fun warmUp(worker: AnalyzerWorker) {
    val random = Random(WARM_UP_SEED)
    repeat(WARM_UP_INPUTS) {
      val input = TokenInputGenerator(random, glue = false).generate()
      worker.analyze(input, null, Int.MAX_VALUE, WARM_UP_TIMEOUT_MILLIS) {
        "warm-up input=\"${escape(input)}\""
      }
    }
  }

  private fun seeds(): List<Long> {
    val runSeed =
      System.getProperty(SEED_PROPERTY)?.takeIf { it.isNotBlank() }?.toLong() ?: Random().nextLong()
    return listOf(FIXED_SEED, runSeed)
  }

  /** Half the inputs are unlimited; the rest get small limits so the cut is exercised. */
  private fun pickLimit(random: Random): Int =
    if (random.nextBoolean()) Int.MAX_VALUE else 1 + random.nextInt(200)

  companion object {
    const val SEED_PROPERTY = "sentry.sql.fuzz.seed"

    /** Bound recorded in plan 01, step 6. */
    const val TIME_BOUND_MILLIS = 250L

    private const val FIXED_SEED = 6019L
    private const val WARM_UP_SEED = 712L
    private const val WARM_UP_INPUTS = 200
    private const val WARM_UP_TIMEOUT_MILLIS = 10_000L
    private const val TOKEN_INPUTS = 2_500
    private const val UTF16_INPUTS = 200
    private const val UTF16_LENGTH = 1_000
  }
}

private fun <T> pick(random: Random, values: List<T>): T = values[random.nextInt(values.size)]

private val LEADING_KEYWORDS =
  listOf("SELECT", "INSERT INTO", "UPDATE", "DELETE FROM", "CALL", "EXEC", "WITH", "CREATE")

/** Generates token soup; the last construct is left unterminated in a third of the inputs. */
private class TokenInputGenerator(private val random: Random, private val glue: Boolean) {
  private val out = StringBuilder()

  fun generate(): String {
    if (random.nextInt(10) != 0) append(pick(random, LEADING_KEYWORDS))
    repeat(random.nextInt(MAX_SEGMENTS)) {
      if (random.nextInt(3) == 0) append(construct(terminated = true))
      else append(pick(random, FREE_TOKENS))
    }
    if (random.nextInt(3) == 0) append(construct(terminated = false))
    return out.toString()
  }

  private fun append(token: String) {
    if (out.isNotEmpty() && !(glue && random.nextBoolean())) out.append(pick(random, SEPARATORS))
    out.append(token)
  }

  private fun construct(terminated: Boolean): String {
    val (open, close) = pick(random, CONSTRUCTS)
    val content = List(random.nextInt(6)) { pick(random, CONTENT) }.joinToString("")
    return open + content + if (terminated) close else ""
  }

  companion object {
    private const val MAX_SEGMENTS = 60

    private val SEPARATORS = listOf(" ", " ", " ", "\n", "\t", "  ", "\r\n")

    private val CONSTRUCTS =
      listOf(
        "'" to "'",
        "N'" to "'",
        "E'" to "'",
        "\"" to "\"",
        "`" to "`",
        "[" to "]",
        "$$" to "$$",
        "\$t$" to "\$t$",
        "/*" to "*/",
        "/*!" to "*/",
        "-- " to "\n",
        "--" to "\n",
        "#" to "\n",
        "//" to "\n",
        "q'[" to "]'",
        "q'{" to "}'",
      )

    private val CONTENT =
      listOf(
        "a",
        "users",
        "Zq9xK7",
        "straße",
        " ",
        "1",
        "2.5",
        ",",
        "(",
        ")",
        ";",
        "=",
        "'",
        "''",
        "\"",
        "`",
        "\\",
        "/*",
        "*/",
        "--",
        "#",
        "$$",
        "[",
        "]",
        "\n",
        "?",
        ":p",
        "@p",
        "$1",
        "\uD835\uDC9C",
      )

    private val FREE_TOKENS =
      listOf(
        "SELECT",
        "FROM",
        "WHERE",
        "INSERT",
        "INTO",
        "VALUES",
        "UPDATE",
        "SET",
        "DELETE",
        "CALL",
        "EXEC",
        "EXECUTE",
        "WITH",
        "AS",
        "JOIN",
        "ON",
        "IN",
        "AND",
        "OR",
        "NULL",
        "CREATE",
        "TABLE",
        "USER",
        "IDENTIFIED",
        "BY",
        "UNION",
        "users",
        "t",
        "col_a",
        "straße",
        "x1",
        "1",
        "-2.5",
        "1e-5",
        "0x1F",
        "=",
        "<>",
        "+",
        "-",
        "*",
        "/",
        ",",
        ".",
        ";",
        "(",
        ")",
        "{",
        "}",
        "?",
        "?1",
        ":name",
        "@p",
        "$1",
        "\\",
        "$",
        "*/",
        "]",
        "||",
      )
  }
}
