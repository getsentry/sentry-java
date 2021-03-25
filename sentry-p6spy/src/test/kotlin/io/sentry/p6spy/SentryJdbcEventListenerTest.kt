package io.sentry.p6spy

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.p6spy.engine.spy.P6DataSource
import io.sentry.IHub
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import org.hsqldb.jdbc.JDBCDataSource
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SentryJdbcEventListenerTest {

    class Fixture {
        private val hub = mock<IHub>()
        val tx = SentryTracer(TransactionContext("name", "op"), hub)
        val actualDataSource = JDBCDataSource()

        fun getSut(withRunningTransaction: Boolean = true): DataSource {
            if (withRunningTransaction) {
                whenever(hub.span).thenReturn(tx)
            }
            actualDataSource.setURL("jdbc:hsqldb:mem:testdb")

            actualDataSource.connection.use {
                it.prepareStatement("CREATE TABLE foo (id int)").execute()
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
        val sut = fixture.getSut()

        try {
            sut.connection.use {
                it.prepareStatement("INSERT INTO foo VALUES ('x')").executeUpdate()
            }
        } catch (e: Exception) {
        }

        assertEquals(1, fixture.tx.children.size)
        assertEquals("INSERT INTO foo VALUES ('x')", fixture.tx.children[0].description)
        assertEquals("db.query", fixture.tx.children[0].operation)
        assertEquals(SpanStatus.INTERNAL_ERROR, fixture.tx.children[0].status)
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
}
