package io.sentry.jdbc

import com.p6spy.engine.spy.P6DataSource
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.protocol.SdkVersion
import org.hsqldb.jdbc.JDBCDataSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SentryJdbcEventListenerTest {

    class Fixture {
        val hub = mock<IHub>().apply {
            whenever(options).thenReturn(
                SentryOptions().apply {
                    sdkVersion = SdkVersion("test", "1.2.3")
                }
            )
        }
        lateinit var tx: SentryTracer
        val actualDataSource = JDBCDataSource()

        fun getSut(withRunningTransaction: Boolean = true, existingRow: Int? = null): DataSource {
            tx = SentryTracer(TransactionContext("name", "op"), hub)
            if (withRunningTransaction) {
                whenever(hub.span).thenReturn(tx)
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

            val sentryQueryExecutionListener = SentryJdbcEventListener(hub)
            val p6spyDataSource = P6DataSource(actualDataSource)
            p6spyDataSource.setJdbcEventListenerFactory { sentryQueryExecutionListener }
            return p6spyDataSource
        }
    }

    private val fixture = Fixture()

    @AfterTest
    fun clean() {
        fixture.actualDataSource.connection.use {
            it.prepareStatement("drop table foo").execute()
        }
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
            sut.connection.use {
                it.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate()
            }
        } catch (e: Exception) {
        }

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
    fun `sets SDKVersion Info`() {
        val sut = fixture.getSut()
        assertNotNull(fixture.hub.options.sdkVersion!!.integrations)
        assert(fixture.hub.options.sdkVersion!!.integrations!!.contains("JdbcEventListener"))
        println(fixture.hub.options.sdkVersion!!.version)
        assertNotNull(fixture.hub.options.sdkVersion!!.packages)
        val packageInfo = fixture.hub.options.sdkVersion!!.packages!!.firstOrNull { pkg -> pkg.name == "maven:io.sentry:sentry-jdbc" }
        assertNotNull(packageInfo)
        assert(packageInfo.version == BuildConfig.VERSION_NAME)
    }
}
