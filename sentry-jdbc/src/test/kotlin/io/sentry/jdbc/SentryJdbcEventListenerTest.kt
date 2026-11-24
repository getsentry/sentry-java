package io.sentry.jdbc

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.p6spy.engine.common.ConnectionInformation
import com.p6spy.engine.common.StatementInformation
import com.p6spy.engine.spy.P6DataSource
import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.SpanDataConvention.DB_NAMESPACE_KEY
import io.sentry.SpanDataConvention.DB_NAME_KEY
import io.sentry.SpanDataConvention.DB_QUERY_SUMMARY_KEY
import io.sentry.SpanDataConvention.DB_QUERY_TEXT_KEY
import io.sentry.SpanDataConvention.DB_STORED_PROCEDURE_NAME_KEY
import io.sentry.SpanDataConvention.DB_SYSTEM_KEY
import io.sentry.SpanDataConvention.DB_SYSTEM_NAME_KEY
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.jdbc.DatabaseUtils.DatabaseDetails
import io.sentry.protocol.SdkVersion
import java.sql.Driver
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.hsqldb.jdbc.JDBCDataSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryJdbcEventListenerTest {
  class Fixture {
    lateinit var options: SentryOptions
    val scopes =
      mock<IScopes>().apply { whenever(this.options).thenAnswer { this@Fixture.options } }
    lateinit var tx: SentryTracer
    val actualDataSource = JDBCDataSource().apply { setURL("jdbc:hsqldb:mem:testdb") }

    fun getSut(
      withRunningTransaction: Boolean = true,
      existingRow: Int? = null,
      enableDatabaseTransactionTracing: Boolean = false,
    ): DataSource {
      options =
        SentryOptions().apply {
          sdkVersion = SdkVersion("test", "1.2.3")
          isEnableDatabaseTransactionTracing = enableDatabaseTransactionTracing
        }
      tx = SentryTracer(TransactionContext("name", "op"), scopes)
      if (withRunningTransaction) {
        whenever(scopes.span).thenReturn(tx)
      }
      actualDataSource.connection.use {
        it.prepareStatement("CREATE TABLE foo (id int unique)").execute()
      }
      existingRow?.let { _ ->
        actualDataSource.connection.use {
          val statement = it.prepareStatement("INSERT INTO foo VALUES (?)")
          statement.setInt(1, existingRow)
          statement.executeUpdate()
        }
      }

      val sentryQueryExecutionListener = SentryJdbcEventListener(scopes)
      val p6spyDataSource = P6DataSource(actualDataSource)
      p6spyDataSource.setJdbcEventListenerFactory { sentryQueryExecutionListener }
      return p6spyDataSource
    }

    /** A listener under a running transaction, driven through its p6spy callbacks directly. */
    fun getListener(): SentryJdbcEventListener {
      options = SentryOptions().apply { sdkVersion = SdkVersion("test", "1.2.3") }
      tx = SentryTracer(TransactionContext("name", "op"), scopes)
      whenever(scopes.span).thenReturn(tx)
      return SentryJdbcEventListener(scopes)
    }
  }

  private val fixture = Fixture()

  @AfterTest
  fun clean() {
    fixture.actualDataSource.connection.use {
      it.prepareStatement("drop table foo if exists").execute()
    }
  }

  /** Runs one statement the way p6spy reports it: before and after execute, same connection. */
  private fun SentryJdbcEventListener.execute(url: String?, sql: String) {
    val connection = ConnectionInformation.fromDriver(null as Driver?).apply { setUrl(url) }
    val statement = StatementInformation(connection).apply { statementQuery = sql }
    onBeforeAnyExecute(statement)
    onAfterAnyExecute(statement, 0, null)
  }

  private fun assertNoCanary(span: Span, canary: String) {
    assertThat(span.description.orEmpty()).doesNotContain(canary)
    span.data.forEach { (key, value) ->
      assertWithMessage(key).that(value.toString()).doesNotContain(canary)
    }
  }

  @Test
  fun `resolves database details per connection URL`() {
    val sut = fixture.getListener()

    sut.execute("jdbc:postgresql://localhost:5432/orders", "SELECT 1")
    sut.execute(
      "jdbc:mysql://localhost:3306/users",
      "SELECT a FROM t WHERE x = 1 # it's\n AND p = 'secret'",
    )

    val (postgres, mysql) = fixture.tx.children
    assertThat(postgres.data[DB_SYSTEM_KEY]).isEqualTo("postgresql")
    assertThat(mysql.data[DB_SYSTEM_KEY]).isEqualTo("mysql")
    assertThat(mysql.data[DB_NAME_KEY]).isEqualTo("users")
    assertThat(mysql.description).isEqualTo("SELECT a FROM t WHERE x = ? AND p = ?")
    assertNoCanary(mysql, "secret")
  }

  @Test
  fun `does not read the statement when the jdbc span origin is ignored`() {
    // OpenTelemetry's AGENT and AGENTLESS_SPRING modes ignore auto.db.jdbc by default
    val sut = fixture.getListener()
    fixture.options.setIgnoredSpanOrigins(listOf("auto.db.jdbc"))
    val connection = mock<ConnectionInformation>()
    val statement =
      mock<StatementInformation>().apply {
        whenever(connectionInformation).thenReturn(connection)
      }

    sut.onBeforeAnyExecute(statement)
    sut.onAfterAnyExecute(statement, 0, null)

    verify(statement, never()).sql
    verify(connection, never()).url
    assertThat(fixture.tx.children).isEmpty()
  }

  @Test
  fun `resolves database details for more connection URLs than are cached`() {
    // 65 URLs (url, db.system, db.name), alternating systems so a wrong entry shows
    val connections =
      (0..64).map {
        if (it % 2 == 0) Triple("jdbc:postgresql://h/db$it", "postgresql", "db$it")
        else Triple("jdbc:mysql://h/db$it", "mysql", "db$it")
      }
    val sut = fixture.getListener()

    repeat(2) { connections.forEach { (url, _, _) -> sut.execute(url, "SELECT 1") } }

    assertThat(fixture.tx.children).hasSize(2 * connections.size)
    (connections + connections).zip(fixture.tx.children).forEach { (connection, span) ->
      val (url, dbSystem, dbName) = connection
      assertWithMessage(url).that(span.data[DB_SYSTEM_KEY]).isEqualTo(dbSystem)
      assertWithMessage(url).that(span.data[DB_NAME_KEY]).isEqualTo(dbName)
    }
  }

  @Test
  fun `keeps literals and comments out of every span field`() {
    // input to (description and db.query.text, db.query.summary, db.stored_procedure.name)
    val cases =
      listOf(
        Triple(
          "SELECT * FROM users WHERE email = 'canary@x.io'",
          "SELECT * FROM users WHERE email = ?",
          "SELECT users" to null,
        ),
        Triple(
          "SELECT * FROM users /* canary */ WHERE id = 1",
          "SELECT * FROM users WHERE id = ?",
          "SELECT users" to null,
        ),
        Triple(
          "SELECT * FROM users WHERE id = 1 -- canary",
          "SELECT * FROM users WHERE id = ?",
          "SELECT users" to null,
        ),
        // STOPPED: unterminated literal
        Triple(
          "SELECT * FROM users WHERE name = 'canary",
          "SELECT * FROM users WHERE name = ?",
          "SELECT users" to null,
        ),
        Triple("CALL get_user('canary')", "CALL get_user(?)", "CALL get_user" to "get_user"),
      )
    val sut = fixture.getListener()

    cases.forEach { (sql, _, _) -> sut.execute("jdbc:postgresql://localhost/app", sql) }

    assertThat(fixture.tx.children).hasSize(cases.size)
    cases.zip(fixture.tx.children).forEach { (case, span) ->
      val (sql, text, summaryAndProcedure) = case
      val (summary, procedure) = summaryAndProcedure
      assertWithMessage(sql).that(span.description).isEqualTo(text)
      assertWithMessage(sql).that(span.data[DB_QUERY_TEXT_KEY]).isEqualTo(text)
      assertWithMessage(sql).that(span.data[DB_QUERY_SUMMARY_KEY]).isEqualTo(summary)
      assertWithMessage(sql).that(span.data[DB_STORED_PROCEDURE_NAME_KEY]).isEqualTo(procedure)
      assertNoCanary(span, "canary")
    }
  }

  @Test
  fun `reports the truncated sanitized text for a query over the limit`() {
    val sql =
      "SELECT a FROM orders WHERE note = 'canary' OR " +
        (0 until 4000).joinToString(" OR ") { "c$it IS NULL" }
    val sut = fixture.getListener()

    sut.execute("jdbc:postgresql://localhost/app", sql)

    val span = fixture.tx.children.single()
    assertThat(span.description).startsWith("SELECT a FROM orders WHERE note = ? OR c0 IS NULL OR ")
    assertThat(span.description!!.length).isAtMost(32 * 1024)
    assertThat(span.data[DB_QUERY_TEXT_KEY]).isEqualTo(span.description)
    assertThat(span.data[DB_QUERY_SUMMARY_KEY]).isEqualTo("SELECT orders")
    assertNoCanary(span, "canary")
  }

  @Test
  fun `sanitizes queries on a connection without URL`() {
    val sut = fixture.getListener()

    sut.execute(null, "SELECT * FROM users WHERE email = 'canary@x.io'")

    val span = fixture.tx.children.single()
    assertThat(span.description).isEqualTo("SELECT * FROM users WHERE email = ?")
    assertThat(span.data).doesNotContainKey(DB_SYSTEM_KEY)
    assertNoCanary(span, "canary")
  }

  @Test
  fun `creates spans for successful calls`() {
    val sut = fixture.getSut()

    sut.connection.use {
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.prepareStatement("INSERT INTO foo VALUES (2)").executeUpdate()
    }

    assertEquals(2, fixture.tx.children.size)
    fixture.tx.children.forEach {
      assertEquals(SpanStatus.OK, it.status)
      assertEquals("db.query", it.operation)
    }
    assertEquals("INSERT INTO foo VALUES (?)", fixture.tx.children[0].description)
    assertEquals("INSERT INTO foo VALUES (?)", fixture.tx.children[1].description)
  }

  @Test
  fun `creates spans for calls resulting in error`() {
    val sut = fixture.getSut(existingRow = 1)

    try {
      sut.connection.use { it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate() }
    } catch (e: Exception) {}

    assertEquals(1, fixture.tx.children.size)
    assertEquals(SpanStatus.INTERNAL_ERROR, fixture.tx.children[0].status)
    assertEquals("INSERT INTO foo VALUES (?)", fixture.tx.children[0].description)
    assertEquals("db.query", fixture.tx.children[0].operation)
  }

  @Test
  fun `does not create spans when there is no running transactions`() {
    val sut = fixture.getSut(withRunningTransaction = false)

    sut.connection.use {
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.prepareStatement("INSERT INTO foo VALUES (2)").executeUpdate()
    }

    assertTrue(fixture.tx.children.isEmpty())
  }

  @Test
  fun `adds trace origin to span`() {
    val sut = fixture.getSut()

    sut.connection.use { it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate() }

    assertEquals("auto.db.jdbc", fixture.tx.children.first().spanContext.origin)
  }

  @Test
  fun `sets SDKVersion Info`() {
    val sut = fixture.getSut()
    assertNotNull(fixture.scopes.options.sdkVersion)
    assert(fixture.scopes.options.sdkVersion!!.integrationSet.contains("JDBC"))
    val packageInfo =
      fixture.scopes.options.sdkVersion!!.packageSet.firstOrNull { pkg ->
        pkg.name == "maven:io.sentry:sentry-jdbc"
      }
    assertNotNull(packageInfo)
    assert(packageInfo.version == BuildConfig.VERSION_NAME)
  }

  @Test
  fun `sets database details and query data`() {
    val sut = fixture.getSut()

    sut.connection.use { it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate() }

    assertThat(fixture.tx.children.first().data)
      .containsAtLeast(
        DB_SYSTEM_KEY,
        "hsqldb",
        DB_SYSTEM_NAME_KEY,
        "hsqldb",
        DB_NAME_KEY,
        "testdb",
        DB_NAMESPACE_KEY,
        "testdb",
        DB_QUERY_TEXT_KEY,
        "INSERT INTO foo VALUES (?)",
        DB_QUERY_SUMMARY_KEY,
        "INSERT foo",
      )
  }

  @Test
  fun `only parses database details once`() {
    Mockito.mockStatic(DatabaseUtils::class.java).use { utils ->
      var invocationCount = 0
      utils
        .`when`<Any> { DatabaseUtils.readFrom(any<ConnectionInformation>()) }
        .thenAnswer {
          invocationCount++
          DatabaseDetails("a", "b")
        }
      val sut = fixture.getSut()

      sut.connection.use {
        it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
        it.prepareStatement("INSERT INTO foo VALUES (2)").executeUpdate()
      }

      sut.connection.use {
        it.prepareStatement("INSERT INTO foo VALUES (3)").executeUpdate()
        it.prepareStatement("INSERT INTO foo VALUES (4)").executeUpdate()
      }

      assertEquals("a", fixture.tx.children.first().data[DB_SYSTEM_KEY])
      assertEquals("b", fixture.tx.children.first().data[DB_NAME_KEY])

      assertEquals(1, invocationCount)
    }
  }

  @Test
  fun `creates span for commit when database transaction tracing is enabled`() {
    val sut = fixture.getSut(enableDatabaseTransactionTracing = true)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.commit()
    }

    val commitSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.commit" }
    assertEquals(1, commitSpans.size)
    assertEquals(SpanStatus.OK, commitSpans[0].status)
    assertEquals("auto.db.jdbc", commitSpans[0].spanContext.origin)
  }

  @Test
  fun `creates span for rollback when database transaction tracing is enabled`() {
    val sut = fixture.getSut(enableDatabaseTransactionTracing = true)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.rollback()
    }

    val rollbackSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.rollback" }
    assertEquals(1, rollbackSpans.size)
    assertEquals(SpanStatus.OK, rollbackSpans[0].status)
    assertEquals("auto.db.jdbc", rollbackSpans[0].spanContext.origin)
  }

  @Test
  fun `commit span has database details`() {
    val sut = fixture.getSut(enableDatabaseTransactionTracing = true)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.commit()
    }

    val commitSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.commit" }
    assertEquals(1, commitSpans.size)
    assertEquals("hsqldb", commitSpans[0].data[DB_SYSTEM_KEY])
    assertEquals("testdb", commitSpans[0].data[DB_NAME_KEY])
  }

  @Test
  fun `rollback span has database details`() {
    val sut = fixture.getSut(enableDatabaseTransactionTracing = true)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.rollback()
    }

    val rollbackSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.rollback" }
    assertEquals(1, rollbackSpans.size)
    assertEquals("hsqldb", rollbackSpans[0].data[DB_SYSTEM_KEY])
    assertEquals("testdb", rollbackSpans[0].data[DB_NAME_KEY])
  }

  @Test
  fun `does not create commit span when there is no running transaction`() {
    val sut =
      fixture.getSut(withRunningTransaction = false, enableDatabaseTransactionTracing = true)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.commit()
    }

    val commitSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.commit" }
    assertTrue(commitSpans.isEmpty())
  }

  @Test
  fun `does not create rollback span when there is no running transaction`() {
    val sut =
      fixture.getSut(withRunningTransaction = false, enableDatabaseTransactionTracing = true)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.rollback()
    }

    val rollbackSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.rollback" }
    assertTrue(rollbackSpans.isEmpty())
  }

  @Test
  fun `creates span for transaction begin when setAutoCommit false and database transaction tracing is enabled`() {
    val sut = fixture.getSut(enableDatabaseTransactionTracing = true)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.commit()
    }

    val beginSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.begin" }
    assertEquals(1, beginSpans.size)
    assertEquals(SpanStatus.OK, beginSpans[0].status)
    assertEquals("auto.db.jdbc", beginSpans[0].spanContext.origin)
  }

  @Test
  fun `transaction begin span has database details`() {
    val sut = fixture.getSut(enableDatabaseTransactionTracing = true)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.commit()
    }

    val beginSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.begin" }
    assertEquals(1, beginSpans.size)
    assertEquals("hsqldb", beginSpans[0].data[DB_SYSTEM_KEY])
    assertEquals("testdb", beginSpans[0].data[DB_NAME_KEY])
  }

  @Test
  fun `does not create begin span when already in manual commit mode`() {
    val sut = fixture.getSut(enableDatabaseTransactionTracing = true)

    sut.connection.use {
      it.autoCommit = false
      it.autoCommit = false // setting again should not create another span
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.commit()
    }

    val beginSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.begin" }
    assertEquals(1, beginSpans.size)
  }

  @Test
  fun `does not create begin span when there is no running transaction`() {
    val sut =
      fixture.getSut(withRunningTransaction = false, enableDatabaseTransactionTracing = true)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.commit()
    }

    val beginSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.begin" }
    assertTrue(beginSpans.isEmpty())
  }

  @Test
  fun `does not create transaction spans when database transaction tracing is disabled`() {
    val sut = fixture.getSut(enableDatabaseTransactionTracing = false)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.commit()
    }

    val beginSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.begin" }
    val commitSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.commit" }
    assertTrue(beginSpans.isEmpty())
    assertTrue(commitSpans.isEmpty())
    // Query spans should still be created
    val querySpans = fixture.tx.children.filter { it.operation == "db.query" }
    assertEquals(1, querySpans.size)
  }

  @Test
  fun `does not create rollback span when database transaction tracing is disabled`() {
    val sut = fixture.getSut(enableDatabaseTransactionTracing = false)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.rollback()
    }

    val rollbackSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.rollback" }
    assertTrue(rollbackSpans.isEmpty())
    // Query spans should still be created
    val querySpans = fixture.tx.children.filter { it.operation == "db.query" }
    assertEquals(1, querySpans.size)
  }
}
