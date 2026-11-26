// Inputs adapted from OpenTelemetry Java Instrumentation (SqlQueryAnalyzerTest) and
// OpenTelemetry .NET Contrib (SqlProcessorTests).
// Copyright The OpenTelemetry Authors.
// Licensed under the Apache License, Version 2.0.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/2acd9a5f1153e725a3b80e9b90ad55b385576fe8/instrumentation-api-incubator/src/test/java/io/opentelemetry/instrumentation/api/incubator/semconv/db/SqlQueryAnalyzerTest.java
// https://github.com/open-telemetry/opentelemetry-dotnet-contrib/blob/4e3aba9ddf89f9accac004f44f32ebde335dd115/test/OpenTelemetry.Contrib.Shared.Tests/SqlProcessorTests.cs
package io.sentry.util.sql

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Owns stability for oversized and degenerate input, under both dialects:
 * [SqlQueryAnalyzer.analyze] completes within [TIME_BOUND_MILLIS] on an
 * [AnalyzerWorker.STACK_SIZE_BYTES] stack, throws nothing (a `StackOverflowError` would surface
 * here), fails nowhere internally, respects `maxQueryTextLength` and never splits a surrogate pair.
 * Outputs are not asserted (plan 08, Tests).
 */
@RunWith(Parameterized::class)
class SqlQueryAnalyzerPathologicalTest(
  @Suppress("unused") private val name: String,
  private val input: Input,
  private val dbSystemName: String?,
) {

  class Input(
    val name: String,
    val maxQueryTextLength: Int = Int.MAX_VALUE,
    val build: () -> String,
  )

  @Test
  fun `completes within the bound without failing, exceeding the limit or splitting surrogates`() {
    val sql = input.build()
    val describe = { "${input.name} (${sql.length} chars) under $dbSystemName" }

    val query =
      AnalyzerWorker().use {
        it.analyze(sql, dbSystemName, input.maxQueryTextLength, TIME_BOUND_MILLIS, describe)
      }

    assertHasText(query, describe)
    assertWithinLimit(query, input.maxQueryTextLength, describe)
    assertNoLoneSurrogate(sql, query, describe)
  }

  companion object {
    /** Bound recorded in plan 01, step 6. */
    const val TIME_BOUND_MILLIS = 2_000L

    private const val KIB = 1024
    private const val D5_JDBC_LIMIT = 32 * KIB

    private val DIGITS = (0 until 10_000).joinToString("")

    private val INPUTS =
      listOf(
        Input("10k nested parens") { "SELECT " + "(".repeat(10_000) + "1" + ")".repeat(10_000) },
        Input("1 MiB of statements") {
          val statement = "SELECT a, b FROM t WHERE c = 'Zq9xK7' AND d IN (1, 2, 3) /* Zq9xK7 */; "
          // Just under 1 MiB: longer input is not analyzed at all (D66).
          statement.repeat(KIB * KIB / statement.length)
        },
        // An even count closes the literal (`''` is an escaped quote); an odd count leaves it
        // unterminated.
        Input("64 KiB of single quotes, closed") { "SELECT " + "'".repeat(64 * KIB) },
        Input("64 KiB of single quotes, unterminated") { "SELECT " + "'".repeat(64 * KIB + 1) },
        Input("64 KiB of comment openers") { "/*".repeat(32 * KIB) },
        // OTel .NET SqlProcessorTests#…RepeatedUnterminatedEscapedIdentifiersInFromClause…
        Input("4096 unterminated brackets") {
          "SELECT * FROM " +
            "[".repeat(4096) +
            " WHERE CustomerName = 'secret-name' AND Id = 123 AND Token = 0xDEADBEEF"
        },
        Input("128 KiB of opening brackets, closed") { "SELECT " + "[".repeat(128 * KIB) + "]" },
        Input("128 KiB of opening brackets, unterminated") { "SELECT " + "[".repeat(128 * KIB) },
        Input("128 KiB of hash signs on one line") { "SELECT 1 " + "#".repeat(128 * KIB) },
        Input("128 KiB of double slashes on one line") { "SELECT 1 " + "//".repeat(64 * KIB) },
        Input("128 KiB of --a on one line") { "SELECT 1 " + "--a".repeat(128 * KIB / 3) },
        Input("128 KiB of q'[ on one line") { "SELECT 1 " + "q'[".repeat(128 * KIB / 3) },
        Input("128 KiB of dollar signs") { "SELECT " + "$".repeat(128 * KIB) },
        // OTel Java SqlQueryAnalyzerTest#longInStatementDoesntCauseStackOverflow
        Input("10001-item IN list") {
          "select col from table where col in (" + "?,".repeat(10_000) + "?)"
        },
        // OTel Java SqlQueryAnalyzerTest#veryLongNumbersAreOk
        Input("39k-digit number") { "SELECT $DIGITS" },
        // OTel Java SqlQueryAnalyzerTest#veryLongNumbersAtEndOfTableAreOk
        Input("39k-character identifier") { "SELECT A$DIGITS" },
        // OTel Java SqlQueryAnalyzerTest#test32kTruncation
        Input("10k statements at the 32 KiB limit", maxQueryTextLength = D5_JDBC_LIMIT) {
          "SELECT * FROM TABLE WHERE FIELD = 1234 AND ".repeat(10_000)
        },
        // OTel Java SqlQueryAnalyzerTest#queryTextTruncationDoesNotSplitSurrogatePair: the limit
        // falls between the two halves of U+1D49C, a letter outside the BMP.
        Input("surrogate pair at the 32 KiB limit", maxQueryTextLength = D5_JDBC_LIMIT) {
          // The high surrogate is the limit's last character.
          "SELECT " + "A".repeat(D5_JDBC_LIMIT - "SELECT ".length - 1) + "\uD835\uDC9C FROM t"
        },
        // As above, without whitespace, so the wrapper cuts at the limit itself.
        Input(
          "surrogate pair at the 32 KiB limit, no whitespace",
          maxQueryTextLength = D5_JDBC_LIMIT,
        ) {
          "A".repeat(D5_JDBC_LIMIT - 1) + "\uD835\uDC9C"
        },
      )

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters(): List<Array<Any?>> = INPUTS.flatMap { input ->
      DIALECTS.map { dbSystemName ->
        arrayOf(input.name + " / " + dbSystemName, input, dbSystemName)
      }
    }
  }
}
