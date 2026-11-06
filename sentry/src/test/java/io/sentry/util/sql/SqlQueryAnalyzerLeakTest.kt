package io.sentry.util.sql

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Owns leaks for the shared case file: no declared secret, and no fragment of one, reaches any
 * output field. Expected outputs are owned by [SqlQueryAnalyzerCasesTest].
 *
 * Every prefix of the input is analyzed too, so a construct that is unterminated at some cut point
 * cannot leak. The full input is checked first with a whole-secret message (the canary check);
 * since every declared secret is at least [FRAGMENT_LENGTH] characters long, the fragment check on
 * the full input would subsume it, and the two are one test (plan 01, step 6).
 */
@RunWith(Parameterized::class)
class SqlQueryAnalyzerLeakTest(
  @Suppress("unused") private val id: String,
  private val case: SqlQueryAnalyzerCasesTest.Case,
) {

  @Test
  fun `no secret or secret fragment leaks from the input or any prefix`() {
    val full = analyze(case.input)
    for ((field, value) in full.fields()) {
      for (secret in case.secrets) {
        assertWithMessage("secret '%s' in %s", secret, field)
          .that(value ?: "")
          .doesNotContain(secret)
      }
    }

    for (length in case.input.length downTo 1) {
      val prefix = case.input.substring(0, length)
      val fragments = secretPartsIn(prefix).flatMap { fragments(it) }.toSet()
      if (fragments.isEmpty()) continue
      for ((field, value) in analyze(prefix).fields()) {
        val leaked = fragments.firstOrNull { value?.contains(it) == true } ?: continue
        assertWithMessage(
            "prefix of length %s (%s): fragment '%s' in %s",
            length,
            escape(prefix),
            leaked,
            field,
          )
          .that(value)
          .doesNotContain(leaked)
      }
    }
  }

  private fun analyze(sql: String): SqlQuery =
    SqlQueryAnalyzer.analyze(sql, case.dialect, case.maxQueryTextLength)

  /** The part of each secret occurrence that lies inside [prefix]. */
  private fun secretPartsIn(prefix: String): List<String> {
    val parts = mutableListOf<String>()
    for (secret in case.secrets) {
      var start = case.input.indexOf(secret)
      while (start in 0 until prefix.length) {
        parts += secret.substring(0, minOf(secret.length, prefix.length - start))
        start = case.input.indexOf(secret, start + 1)
      }
    }
    return parts
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters(): List<Array<Any>> =
      SqlQueryAnalyzerCasesTest.loadCases()
        .filter { it.secrets.isNotEmpty() }
        .map { arrayOf(it.id, it) }
  }
}
