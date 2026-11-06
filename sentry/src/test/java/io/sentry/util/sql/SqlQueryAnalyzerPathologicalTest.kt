package io.sentry.util.sql

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Owns stability for oversized and degenerate input, under every dialect:
 * [SqlQueryAnalyzer.analyze] completes within [TIME_BOUND_MILLIS] on a
 * [AnalyzerWorker.STACK_SIZE_BYTES] stack, throws nothing (a `StackOverflowError` would surface
 * here), respects `maxQueryTextLength`, never splits a surrogate pair, and leaks none of the
 * input's secrets. Exact outputs are owned by [SqlQueryAnalyzerCasesTest] (plan 01, step 6).
 */
@RunWith(Parameterized::class)
class SqlQueryAnalyzerPathologicalTest(
  @Suppress("unused") private val name: String,
  private val input: Input,
  private val dialectName: String,
) {

  class Input(
    val name: String,
    val secrets: List<String> = emptyList(),
    val maxQueryTextLength: Int = Int.MAX_VALUE,
    val build: () -> String,
  )

  @Test
  fun `completes within the bound without throwing, splitting surrogates or leaking`() {
    val sql = input.build()
    val dialect = ALL_DIALECTS.getValue(dialectName)
    val describe = { "${input.name} (${sql.length} chars) under $dialectName" }

    val query =
      AnalyzerWorker().use {
        it.analyze(sql, dialect, input.maxQueryTextLength, TIME_BOUND_MILLIS, describe)
      }

    assertWithMessage("text longer than the limit; %s", describe())
      .that(query.queryText?.length ?: 0)
      .isAtMost(input.maxQueryTextLength)
    for ((field, value) in query.fields()) {
      if (value == null) continue
      assertWithMessage("lone surrogate in %s; %s", field, describe())
        .that(hasLoneSurrogate(value))
        .isFalse()
      for (secret in input.secrets) {
        assertWithMessage("secret '%s' in %s; %s", secret, field, describe())
          .that(value)
          .doesNotContain(secret)
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

  companion object {
    /** Bound recorded in plan 01, step 6. */
    const val TIME_BOUND_MILLIS = 2_000L

    private const val KIB = 1024
    private const val D5_JDBC_LIMIT = 32 * KIB

    private val DIGITS = (0 until 10_000).joinToString("")

    private val INPUTS =
      listOf(
        Input("10k nested parens") {
          "SELECT " + "(".repeat(10_000) + "1" + ")".repeat(10_000)
        },
        Input("1 MiB of statements", secrets = listOf("Zq9xK7")) {
          val statement = "SELECT a, b FROM t WHERE c = 'Zq9xK7' AND d IN (1, 2, 3) /* Zq9xK7 */; "
          statement.repeat(KIB * KIB / statement.length + 1)
        },
        Input("64 KiB of single quotes") { "'".repeat(64 * KIB) },
        Input("64 KiB of comment openers") { "/*".repeat(32 * KIB) },
        // OTel .NET SqlProcessorTests#…RepeatedUnterminatedEscapedIdentifiersInFromClause…
        Input("4096 unterminated brackets", secrets = listOf("secret-name", "123", "DEADBEEF")) {
          "SELECT * FROM " +
            "[".repeat(4096) +
            " WHERE CustomerName = 'secret-name' AND Id = 123 AND Token = 0xDEADBEEF"
        },
        // OTel Java SqlQueryAnalyzerTest#longInStatementDoesntCauseStackOverflow
        Input("10001-item IN list") {
          "select col from table where col in (" + "?,".repeat(10_000) + "?)"
        },
        // OTel Java SqlQueryAnalyzerTest#veryLongNumbersAreOk
        Input("39k-digit number", secrets = listOf("1234")) { "SELECT $DIGITS" },
        // OTel Java SqlQueryAnalyzerTest#veryLongNumbersAtEndOfTableAreOk
        Input("39k-character identifier") { "SELECT A$DIGITS" },
        // OTel Java SqlQueryAnalyzerTest#test32kTruncation
        Input(
          "10k statements at the 32 KiB limit",
          secrets = listOf("1234"),
          maxQueryTextLength = D5_JDBC_LIMIT,
        ) {
          "SELECT * FROM TABLE WHERE FIELD = 1234 AND ".repeat(10_000)
        },
        // OTel Java SqlQueryAnalyzerTest#queryTextTruncationDoesNotSplitSurrogatePair: the limit
        // falls between the two halves of U+1D49C, a letter outside the BMP.
        Input("surrogate pair at the 32 KiB limit", maxQueryTextLength = D5_JDBC_LIMIT) {
          // The high surrogate is the limit's last character.
          "SELECT " + "A".repeat(D5_JDBC_LIMIT - "SELECT ".length - 1) + "\uD835\uDC9C FROM t"
        },
      )

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters(): List<Array<Any>> = INPUTS.flatMap { input ->
      ALL_DIALECTS.keys.map { dialect -> arrayOf("${input.name} / $dialect", input, dialect) }
    }
  }
}
