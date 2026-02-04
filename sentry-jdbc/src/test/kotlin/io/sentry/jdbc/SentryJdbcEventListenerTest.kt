package io.sentry.jdbc

import com.p6spy.engine.common.ConnectionInformation
import com.p6spy.engine.spy.P6DataSource
import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention.DB_NAME_KEY
import io.sentry.SpanDataConvention.DB_SYSTEM_KEY
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.jdbc.DatabaseUtils.DatabaseDetails
import io.sentry.protocol.SdkVersion
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
import org.mockito.kotlin.whenever

class SentryJdbcEventListenerTest {
  class Fixture {
    val scopes =
      mock<IScopes>().apply {
        whenever(options)
          .thenReturn(SentryOptions().apply { sdkVersion = SdkVersion("test", "1.2.3") })
      }
    lateinit var tx: SentryTracer
    val actualDataSource = JDBCDataSource()

    fun getSut(withRunningTransaction: Boolean = true, existingRow: Int? = null): DataSource {
      tx = SentryTracer(TransactionContext("name", "op"), scopes)
      if (withRunningTransaction) {
        whenever(scopes.span).thenReturn(tx)
      }
      actualDataSource.setURL("jdbc:hsqldb:mem:testdb")

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
  }

  private val fixture = Fixture()

  @AfterTest
  fun clean() {
    fixture.actualDataSource.connection.use { it.prepareStatement("drop table foo").execute() }
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
    assertEquals("INSERT INTO foo VALUES (1)", fixture.tx.children[0].description)
    assertEquals("INSERT INTO foo VALUES (2)", fixture.tx.children[1].description)
  }

  @Test
  fun `creates spans for calls resulting in error`() {
    val sut = fixture.getSut(existingRow = 1)

    try {
      sut.connection.use { it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate() }
    } catch (e: Exception) {}

    assertEquals(1, fixture.tx.children.size)
    assertEquals(SpanStatus.INTERNAL_ERROR, fixture.tx.children[0].status)
    assertEquals("INSERT INTO foo VALUES (1)", fixture.tx.children[0].description)
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
  fun `sets database details`() {
    val sut = fixture.getSut()

    sut.connection.use { it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate() }

    assertEquals("hsqldb", fixture.tx.children.first().data[DB_SYSTEM_KEY])
    assertEquals("testdb", fixture.tx.children.first().data[DB_NAME_KEY])
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
  fun `creates span for commit`() {
    val sut = fixture.getSut()

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
  fun `creates span for rollback`() {
    val sut = fixture.getSut()

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
    val sut = fixture.getSut()

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
    val sut = fixture.getSut()

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
    val sut = fixture.getSut(withRunningTransaction = false)

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
    val sut = fixture.getSut(withRunningTransaction = false)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.rollback()
    }

    val rollbackSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.rollback" }
    assertTrue(rollbackSpans.isEmpty())
  }

  @Test
  fun `creates span for transaction begin when setAutoCommit false`() {
    val sut = fixture.getSut()

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
    val sut = fixture.getSut()

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
    val sut = fixture.getSut()

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
    val sut = fixture.getSut(withRunningTransaction = false)

    sut.connection.use {
      it.autoCommit = false
      it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
      it.commit()
    }

    val beginSpans = fixture.tx.children.filter { it.operation == "db.sql.transaction.begin" }
    assertTrue(beginSpans.isEmpty())
  }
}
