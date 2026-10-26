package io.sentry.util.sql

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.sentry.util.sql.SqlDialect.Support
import io.sentry.util.sql.SqlDialect.Support.NO
import io.sentry.util.sql.SqlDialect.Support.UNKNOWN
import io.sentry.util.sql.SqlDialect.Support.YES
import kotlin.test.Test

/**
 * The mapping decides which syntax the analyzer trusts per database, so a wrong entry can leak
 * query values. Expected rules mirror the cited table in docs/plans/sanitize-sql-queries/README.md.
 */
class SqlDialectTest {

  private fun rules(dialect: SqlDialect): List<Support> =
    listOf(
      dialect.doubleQuotedIdentifiers,
      dialect.bracketIdentifiers,
      dialect.backslashEscapes,
      dialect.dollarQuotedStrings,
      dialect.hashComments,
      dialect.doubleSlashComments,
      dialect.dashDashCommentNeedsWhitespace,
      dialect.nestedBlockComments,
    )

  private val conservative = List(8) { UNKNOWN }

  @Test
  fun `known systems map to their documented rules`() {
    val expected =
      mapOf(
        "postgresql" to listOf(YES, NO, UNKNOWN, YES, NO, NO, NO, YES),
        "mysql" to listOf(UNKNOWN, NO, UNKNOWN, NO, YES, NO, YES, UNKNOWN),
        "mariadb" to listOf(UNKNOWN, NO, UNKNOWN, NO, YES, NO, YES, UNKNOWN),
        "sqlite" to listOf(UNKNOWN, YES, NO, NO, NO, NO, NO, NO),
        "in-memory" to listOf(UNKNOWN, YES, NO, NO, NO, NO, NO, NO),
        "sqlserver" to listOf(UNKNOWN, YES, NO, NO, NO, NO, NO, YES),
        "oracle" to listOf(YES, NO, NO, NO, NO, NO, NO, UNKNOWN),
        "h2" to listOf(YES, UNKNOWN, NO, YES, NO, YES, NO, YES),
        "hsqldb" to listOf(YES, NO, NO, NO, NO, NO, NO, UNKNOWN),
        "derby" to listOf(YES, NO, NO, NO, NO, NO, NO, YES),
      )

    expected.forEach { (system, rules) ->
      assertWithMessage(system).that(rules(SqlDialect.fromDbSystem(system))).isEqualTo(rules)
    }
  }

  @Test
  fun `db system lookup ignores case`() {
    assertThat(SqlDialect.fromDbSystem("PostgreSQL"))
      .isSameInstanceAs(SqlDialect.fromDbSystem("postgresql"))
  }

  @Test
  fun `unknown or missing systems map to conservative rules`() {
    listOf(null, "", "mongo", "db2", "postgres").forEach { system ->
      assertThat(SqlDialect.fromDbSystem(system)).isSameInstanceAs(SqlDialect.CONSERVATIVE)
    }
    assertThat(rules(SqlDialect.CONSERVATIVE)).isEqualTo(conservative)
  }
}
