package io.sentry.android.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.TransactionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SentrySQLiteDriverTest {
  private class Fixture {
    private val scopes = mock<IScopes>()
    val mockDriver = mock<SQLiteDriver>()
    val mockConnection = mock<SQLiteConnection>()
    val mockStatement = mock<SQLiteStatement>()
    lateinit var sentryTracer: SentryTracer
    lateinit var options: SentryOptions

    fun getSut(): SentrySQLiteDriver {
      options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
      whenever(scopes.options).thenReturn(options)
      sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)
      whenever(scopes.span).thenReturn(sentryTracer)

      return SentrySQLiteDriver(scopes, mockDriver)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `opening connection and running query logs to Sentry`() {
    val driver = fixture.getSut()
    val sql = "SELECT * FROM users"

    whenever(fixture.mockDriver.open("test.db")).thenReturn(fixture.mockConnection)
    whenever(fixture.mockConnection.prepare(sql)).thenReturn(fixture.mockStatement)
    whenever(fixture.mockStatement.step()).thenReturn(true)

    // Open connection, prepare statement, and execute
    val connection = driver.open("test.db")
    val statement = connection.prepare(sql)
    statement.step()

    // Verify span was created
    assertEquals(1, fixture.sentryTracer.children.size)
    val span = fixture.sentryTracer.children.first()
    assertEquals("db.sql.query", span.operation)
    assertEquals(sql, span.description)
  }

  @Test
  fun `on-disk database sets db name in span`() {
    val driver = fixture.getSut()
    val databaseName = "myapp.db"
    val sql = "INSERT INTO users VALUES (1, 'test')"

    whenever(fixture.mockDriver.open(databaseName)).thenReturn(fixture.mockConnection)
    whenever(fixture.mockConnection.prepare(sql)).thenReturn(fixture.mockStatement)
    whenever(fixture.mockStatement.step()).thenReturn(true)

    // Open connection, prepare statement, and execute
    val connection = driver.open(databaseName)
    val statement = connection.prepare(sql)
    statement.step()

    // Verify span was created with correct database name
    assertEquals(1, fixture.sentryTracer.children.size)
    val span = fixture.sentryTracer.children.first()
    assertEquals("db.sql.query", span.operation)
    assertEquals(sql, span.description)
    assertEquals("sqlite", span.getData(SpanDataConvention.DB_SYSTEM_KEY))
    assertEquals(databaseName, span.getData(SpanDataConvention.DB_NAME_KEY))
  }

  @Test
  fun `in-memory database sets db system to in-memory`() {
    val driver = fixture.getSut()
    val sql = "CREATE TABLE temp (id INT)"

    whenever(fixture.mockDriver.open(":memory:")).thenReturn(fixture.mockConnection)
    whenever(fixture.mockConnection.prepare(sql)).thenReturn(fixture.mockStatement)
    whenever(fixture.mockStatement.step()).thenReturn(true)

    // Open in-memory connection, prepare statement, and execute
    val connection = driver.open(":memory:")
    val statement = connection.prepare(sql)
    statement.step()

    // Verify span was created with correct database system
    assertEquals(1, fixture.sentryTracer.children.size)
    val span = fixture.sentryTracer.children.first()
    assertEquals("db.sql.query", span.operation)
    assertEquals(sql, span.description)
    assertEquals("in-memory", span.getData(SpanDataConvention.DB_SYSTEM_KEY))
    // DB_NAME_KEY should not be set for in-memory databases
    assertNotNull(span.getData(SpanDataConvention.DB_SYSTEM_KEY))
    assertEquals(null, span.getData(SpanDataConvention.DB_NAME_KEY))
  }
}
