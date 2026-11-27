// The IN-list input is adapted from OpenTelemetry Java Instrumentation
// (SqlQueryAnalyzerTest#longInStatementDoesntCauseStackOverflow).
// Copyright The OpenTelemetry Authors.
// Licensed under the Apache License, Version 2.0.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/2acd9a5f1153e725a3b80e9b90ad55b385576fe8/instrumentation-api-incubator/src/test/java/io/opentelemetry/instrumentation/api/incubator/semconv/db/SqlQueryAnalyzerTest.java
package io.sentry.util.sql

import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test

/**
 * Owns what upstream's tests don't cover: deep and large input on a smaller stack than upstream
 * runs on. [SqlQueryAnalyzer.analyze] must complete within [TIME_BOUND_MILLIS] on a
 * [STACK_SIZE_BYTES] stack, about half of an Android background thread's, without a
 * `StackOverflowError` or an internal failure. Outputs are not asserted (plan 08, Tests).
 */
class SqlQueryAnalyzerPathologicalTest {

  @Test
  fun `deep and large input completes within the bound on a small stack`() {
    val executor = Executors.newSingleThreadExecutor { runnable ->
      Thread(null, runnable, "sql-analyzer-worker", STACK_SIZE_BYTES).apply { isDaemon = true }
    }
    try {
      // Untimed, so class loading and initialization don't count against the first input.
      executor.submit(Callable { SqlQueryAnalyzer.analyze("SELECT 1", null, Int.MAX_VALUE) }).get()

      for ((name, sql) in INPUTS) {
        // One db.system.name per dialect option: double quotes as literals, and as identifiers.
        for (dbSystemName in listOf(null, "postgresql")) {
          val describe = "$name under $dbSystemName"
          val future =
            executor.submit(Callable { SqlQueryAnalyzer.analyze(sql, dbSystemName, Int.MAX_VALUE) })
          val query =
            try {
              future.get(TIME_BOUND_MILLIS, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
              future.cancel(true)
              throw AssertionError("$describe did not complete within $TIME_BOUND_MILLIS ms")
            } catch (e: ExecutionException) {
              throw AssertionError("$describe threw ${e.cause}", e.cause)
            }

          assertWithMessage("no text, so the analyzer failed internally (R5); %s", describe)
            .that(query.queryText)
            .isNotNull()
        }
      }
    } finally {
      executor.shutdownNow()
    }
  }

  private companion object {
    /** Bound recorded in plan 01, step 6. */
    const val TIME_BOUND_MILLIS = 2_000L

    /** Below the default for Android background threads and HotSpot (both about 1 MiB). */
    const val STACK_SIZE_BYTES = 512L * 1024

    const val MIB = 1024 * 1024

    val INPUTS =
      mapOf(
        "10k nested parens" to "SELECT " + "(".repeat(10_000) + "1" + ")".repeat(10_000),
        "10001-item IN list" to "select col from table where col in (" + "?,".repeat(10_000) + "?)",
        // The grammar stops once its output passes 32 KiB, but reads a single token to its end:
        // the slowest input it gets is one literal just under the 1 MiB skip limit (D66).
        "1 MiB string literal" to "SELECT '" + "x".repeat(MIB - "SELECT ''".length) + "'",
      )
  }
}
