package io.sentry

import io.sentry.test.getCtor
import io.sentry.test.getProperty
import io.sentry.test.injectForField
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Timer
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransactionPerformanceCollectorTest {

    private val className = "io.sentry.TransactionPerformanceCollector"
    private val ctorTypes: Array<Class<*>> = arrayOf(SentryOptions::class.java)
    private val fixture = Fixture()

    private class Fixture {
        lateinit var transaction1: ITransaction
        lateinit var transaction2: ITransaction
        val hub: IHub = mock()
        val options = SentryOptions()
        lateinit var mockTimer: Timer
        var lastScheduledRunnable: Runnable? = null

        val mockExecutorService = object : ISentryExecutorService {
            override fun submit(runnable: Runnable): Future<*> = mock()
            override fun <T> submit(callable: Callable<T>): Future<T> = mock()
            override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
                lastScheduledRunnable = runnable
                return FutureTask {}
            }
            override fun close(timeoutMillis: Long) {}
        }

        init {
            whenever(hub.options).thenReturn(options)
        }

        fun getSut(memoryCollector: IMemoryCollector? = null, cpuCollector: ICpuCollector? = null): TransactionPerformanceCollector {
            options.dsn = "https://key@sentry.io/proj"
            options.executorService = mockExecutorService
            options.setMemoryCollector(memoryCollector ?: JavaMemoryCollector())
            options.setCpuCollector(
                cpuCollector ?: object : ICpuCollector {
                    override fun setup() {}
                    override fun collect(): CpuCollectionData = mock()
                }
            )
            transaction1 = SentryTracer(TransactionContext("", ""), hub)
            transaction2 = SentryTracer(TransactionContext("", ""), hub)
            val collector = TransactionPerformanceCollector(options)
            val timer: Timer = collector.getProperty("timer")
            mockTimer = spy(timer)
            collector.injectForField("timer", mockTimer)
            return collector
        }
    }

    @Test
    fun `when null param is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf<Any?>(null))
        }
    }

    @Test
    fun `when NoOp memoryCollector and cpuCollector are set in options, collect is ignored`() {
        val collector = fixture.getSut(NoOpMemoryCollector.getInstance(), NoOpCpuCollector.getInstance())
        assertIs<NoOpMemoryCollector>(fixture.options.memoryCollector)
        assertIs<NoOpCpuCollector>(fixture.options.cpuCollector)
        collector.start(fixture.transaction1)
        verify(fixture.mockTimer, never()).scheduleAtFixedRate(any(), any<Long>(), any())
    }

    @Test
    fun `collect calls cpuCollector setup`() {
        val cpuCollector = mock<ICpuCollector>()
        val collector = fixture.getSut(NoOpMemoryCollector.getInstance(), cpuCollector)
        collector.start(fixture.transaction1)
        verify(cpuCollector).setup()
    }

    @Test
    fun `when start, timer is scheduled every 100 milliseconds`() {
        val collector = fixture.getSut()
        collector.start(fixture.transaction1)
        verify(fixture.mockTimer).scheduleAtFixedRate(any(), any<Long>(), eq(100))
    }

    @Test
    fun `when stop, timer is stopped`() {
        val collector = fixture.getSut()
        collector.start(fixture.transaction1)
        collector.stop(fixture.transaction1)
        verify(fixture.mockTimer).scheduleAtFixedRate(any(), any<Long>(), eq(100))
        verify(fixture.mockTimer).cancel()
    }

    @Test
    fun `stopping a not collected transaction return null`() {
        val collector = fixture.getSut()
        val data = collector.stop(fixture.transaction1)
        verify(fixture.mockTimer, never()).scheduleAtFixedRate(any(), any<Long>(), eq(100))
        verify(fixture.mockTimer, never()).cancel()
        assertNull(data)
    }

    @Test
    fun `collector collect memory for multiple transactions`() {
        val collector = fixture.getSut()
        collector.start(fixture.transaction1)
        collector.start(fixture.transaction2)
        // Let's sleep to make the collector get values
        Thread.sleep(200)

        val data1 = collector.stop(fixture.transaction1)
        // There is still a transaction running: the timer shouldn't stop now
        verify(fixture.mockTimer, never()).cancel()

        val data2 = collector.stop(fixture.transaction2)
        // There are no more transactions running: the time should stop now
        verify(fixture.mockTimer).cancel()

        // The data returned by the collector is not empty
        assertFalse(data1!!.memoryData.isEmpty())
        assertFalse(data1.cpuData.isEmpty())
        assertFalse(data2!!.memoryData.isEmpty())
        assertFalse(data2.cpuData.isEmpty())
    }

    @Test
    fun `collector times out after 30 seconds`() {
        val collector = fixture.getSut()
        collector.start(fixture.transaction1)
        // Let's sleep to make the collector get values
        Thread.sleep(200)
        verify(fixture.mockTimer, never()).cancel()

        // Let the timeout job stop the collector
        fixture.lastScheduledRunnable?.run()
        verify(fixture.mockTimer).cancel()

        // Data is returned even after the collector times out
        val data1 = collector.stop(fixture.transaction1)
        assertFalse(data1!!.memoryData.isEmpty())
        assertFalse(data1.cpuData.isEmpty())
    }

    @Test
    fun `collector reads MemoryCollector on start`() {
        val collector = fixture.getSut()
        assertNull(collector.getProperty<IMemoryCollector?>("memoryCollector"))
        collector.start(fixture.transaction1)
        assertNotNull(collector.getProperty<IMemoryCollector>("memoryCollector"))
    }
}
