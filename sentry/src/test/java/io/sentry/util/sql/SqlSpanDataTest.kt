package io.sentry.util.sql

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.NoOpLogger
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.TransactionContext
import io.sentry.vendor.otel.sql.OtelSqlSanitizer
import kotlin.test.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * What the JDBC and Android SQLite integrations put on a span: the analyzer's text as description
 * and `db.query.text`, summary and stored procedure only when present. The values themselves are
 * tested in [SqlQueryAnalyzerTest].
 */
class SqlSpanDataTest {

  private val scopes =
    mock<IScopes>().also {
      whenever(it.options).thenReturn(SentryOptions().apply { dsn = "https://key@sentry.io/proj" })
    }

  private fun span(): Span =
    SentryTracer(TransactionContext("name", "op"), scopes).startChild("db.sql.query") as Span

  @Test
  fun `span gets the analyzer's fields, and only those that have a value`() {
    for (sql in
      listOf(
        "CALL get_user('a@b.c')",
        "SELECT * FROM users WHERE email = 'x@y.z'",
        "frobnicate 'secret'",
      )) {
      val expected = SqlQueryAnalyzer.analyze(sql, "postgresql", LIMIT)
      val data = SqlSpanData.analyze(sql, "postgresql", LIMIT, NoOpLogger.getInstance())
      val span = span()

      data.applyTo(span)

      assertThat(data.description).isEqualTo(expected.queryText)
      assertThat(span.data.filterKeys { it.startsWith("db.") })
        .isEqualTo(
          mapOf(
              "db.query.text" to expected.queryText,
              "db.query.summary" to expected.querySummary,
              "db.stored_procedure.name" to expected.storedProcedureName,
            )
            .filterValues { it != null },
        )
    }
  }

  @Test
  fun `db system is mapped to the db system name the analyzer gets`() {
    // Oracle reads double quotes as identifiers only under its semconv name, oracle.db.
    val sql = "SELECT \"Zq9xK7\" FROM t"
    val mapped = SqlQueryAnalyzer.analyze(sql, "oracle.db", LIMIT).queryText
    val unmapped = SqlQueryAnalyzer.analyze(sql, "oracle", LIMIT).queryText
    assertThat(mapped).isNotEqualTo(unmapped)

    val data = SqlSpanData.analyze(sql, "oracle", LIMIT, NoOpLogger.getInstance())

    assertThat(data.description).isEqualTo(mapped)
  }

  @Test
  fun `null query gives no description, no query keys and no failure log`() {
    val logger = mock<ILogger>()
    val data = SqlSpanData.analyze(null, "postgresql", LIMIT, logger)
    val span = span()

    data.applyTo(span)

    assertThat(data.description).isNull()
    assertThat(span.data.keys.filter { it.startsWith("db.") }).isEmpty()
    verifyNoInteractions(logger)
  }

  @Test
  fun `query over 1 MiB gives no description, no query keys and no failure log`() {
    val logger = mock<ILogger>()
    val sql = "SELECT '" + "x".repeat(1024 * 1024) + "'"
    val data = SqlSpanData.analyze(sql, "postgresql", LIMIT, logger)
    val span = span()

    data.applyTo(span)

    assertThat(data.description).isNull()
    assertThat(span.data.keys.filter { it.startsWith("db.") }).isEmpty()
    verifyNoInteractions(logger)
  }

  @Test
  fun `internal failure gives no query keys and logs without the query`() {
    val logger = mock<ILogger>()
    Mockito.mockStatic(OtelSqlSanitizer::class.java).use { sanitizer ->
      sanitizer
        .`when`<Any> { OtelSqlSanitizer.sanitize(any(), any()) }
        .thenThrow(IllegalStateException("lexer bug"))

      val data = SqlSpanData.analyze("SELECT 'Zq9xK7'", "postgresql", LIMIT, logger)
      val span = span()
      data.applyTo(span)

      assertThat(data.description).isNull()
      assertThat(span.data.keys.filter { it.startsWith("db.") }).isEmpty()
    }
    val message = argumentCaptor<String>()
    verify(logger).log(eq(SentryLevel.DEBUG), message.capture())
    assertThat(message.firstValue).doesNotContain("Zq9xK7")
  }

  // Expected values: semantic-conventions model/db/registry.yaml.
  @Test
  fun `db system values map to semconv db system names`() {
    val expected =
      mapOf(
        "postgresql" to "postgresql",
        "mysql" to "mysql",
        "mariadb" to "mariadb",
        "sqlite" to "sqlite",
        "in-memory" to "sqlite",
        "hsqldb" to "hsqldb",
        "derby" to "derby",
        "h2" to "h2database",
        "sqlserver" to "microsoft.sql_server",
        "oracle" to "oracle.db",
        "db2" to "ibm.db2",
        "clickhouse" to "clickhouse",
        "sap" to "sap.hana",
        "mongo" to "mongodb",
        "tc" to null,
        "" to null,
        null to null,
      )

    assertThat(expected.mapValues { SqlSpanData.toDbSystemName(it.key) }).isEqualTo(expected)
  }

  private companion object {
    const val LIMIT = 32 * 1024
  }
}
