package io.sentry.util.sql

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.NoOpLogger
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.TransactionContext
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * What the JDBC and Android SQLite integrations put on a span (plan 07, D52): the sanitized text as
 * description and `db.query.text`, summary and stored procedure only when present.
 */
class SqlSpanDataTest {

  private val scopes =
    mock<IScopes>().also {
      whenever(it.options).thenReturn(SentryOptions().apply { dsn = "https://key@sentry.io/proj" })
    }

  private fun span(): Span =
    SentryTracer(TransactionContext("name", "op"), scopes).startChild("db.sql.query") as Span

  private fun analyze(sql: String?, limit: Int = 32 * 1024): SqlSpanData =
    SqlSpanData.analyze(sql, "postgresql", limit, NoOpLogger.getInstance())

  @Test
  fun `complete query sets text, summary and stored procedure`() {
    val data = analyze("CALL get_user('a@b.c')")
    val span = span()

    data.applyTo(span)

    assertThat(data.description).isEqualTo("CALL get_user(?)")
    assertThat(span.getData("db.query.text")).isEqualTo("CALL get_user(?)")
    assertThat(span.getData("db.query.summary")).isEqualTo("CALL get_user")
    assertThat(span.getData("db.stored_procedure.name")).isEqualTo("get_user")
  }

  @Test
  fun `stopped query keeps its sanitized prefix and sets no absent keys`() {
    val data = analyze("SELECT * FROM users WHERE email = 'x@y.z")
    val span = span()

    data.applyTo(span)

    assertThat(data.description).isEqualTo("SELECT * FROM users WHERE email = ?")
    assertThat(span.getData("db.query.text")).isEqualTo("SELECT * FROM users WHERE email = ?")
    assertThat(span.getData("db.query.summary")).isEqualTo("SELECT users")
    assertThat(span.data).doesNotContainKey("db.stored_procedure.name")
  }

  @Test
  fun `stopped query without a summary sets only the text`() {
    val data = analyze("frobnicate 'secret'")
    val span = span()

    data.applyTo(span)

    assertThat(data.description).isEqualTo("?")
    assertThat(span.getData("db.query.text")).isEqualTo("?")
    assertThat(span.data).doesNotContainKey("db.query.summary")
  }

  @Test
  fun `truncated query keeps the text up to the limit`() {
    val data = analyze("SELECT a, b, c FROM users WHERE email = 'x@y.z' AND name = 'n'", limit = 40)
    val span = span()

    data.applyTo(span)

    assertThat(data.description).isEqualTo("SELECT a, b, c FROM users WHERE email =")
    assertThat(span.getData("db.query.text")).isEqualTo("SELECT a, b, c FROM users WHERE email =")
    assertThat(span.getData("db.query.summary")).isEqualTo("SELECT users")
  }

  @Test
  fun `null query gives no description and no query keys`() {
    val data = analyze(null)
    val span = span()

    data.applyTo(span)

    assertThat(data.description).isNull()
    assertThat(span.data.keys.filter { it.startsWith("db.") }).isEmpty()
  }

  @Test
  fun `query over 1 MiB gives no description, no query keys and no failure log`() {
    // D66: SKIPPED is not an analysis failure, so the ERROR log line must not fire for it.
    val logger = mock<ILogger>()
    val sql = "SELECT '" + "x".repeat(1024 * 1024) + "'"
    val data = SqlSpanData.analyze(sql, "postgresql", 32 * 1024, logger)
    val span = span()

    data.applyTo(span)

    assertThat(data.description).isNull()
    assertThat(span.data.keys.filter { it.startsWith("db.") }).isEmpty()
    verifyNoInteractions(logger)
  }

  /**
   * D55: the integrations' `db.system` values mapped to OTel semconv `db.system.name` values
   * (`semantic-conventions` `model/db/registry.yaml`). Unknown systems get no value.
   */
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
        "mongo" to "mongodb",
        "tc" to null,
        "" to null,
        null to null,
      )

    assertThat(expected.mapValues { SqlSpanData.toDbSystemName(it.key) }).isEqualTo(expected)
  }
}
