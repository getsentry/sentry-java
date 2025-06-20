package io.sentry

import io.sentry.test.DeferredExecutorService
import io.sentry.test.getCtor
import io.sentry.test.getProperty
import io.sentry.test.injectForField
import io.sentry.util.thread.ThreadChecker
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Timer
import java.util.concurrent.RejectedExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultCompositePerformanceCollectorTest {

    private val className = "io.sentry.DefaultCompositePerformanceCollector"
    private val ctorTypes: Array<Class<*>> = arrayOf(SentryOptions::class.java)
    private val fixture = Fixture()
    private val threadChecker = ThreadChecker.getInstance()

    private class Fixture {
        lateinit var transaction1: ITransaction
        lateinit var transaction2: ITransaction
        val id1 = "id1"
        val scopes: IScopes = mock()
        val options = SentryOptions()
        var mockTimer: Timer? = null
        val deferredExecutorService = DeferredExecutorService()

        val mockCpuCollector: IPerformanceSnapshotCollector = object :
            IPerformanceSnapshotCollector {
            override fun setup() {}
            override fun collect(performanceCollectionData: PerformanceCollectionData) {
                performanceCollectionData.cpuUsagePercentage = 1.0
            }
        }

        init {
            whenever(scopes.options).thenReturn(options)
        }

        fun getSut(memoryCollector: IPerformanceSnapshotCollector? = JavaMemoryCollector(), cpuCollector: IPerformanceSnapshotCollector? = mockCpuCollector, executorService: ISentryExecutorService = deferredExecutorService): CompositePerformanceCollector {
            options.dsn = "https://key@sentry.io/proj"
            options.executorService = executorService
            if (cpuCollector != null) {
                options.addPerformanceCollector(cpuCollector)
            }
            if (memoryCollector != null) {
                options.addPerformanceCollector(memoryCollector)
            }
            transaction1 = SentryTracer(TransactionContext("", ""), scopes)
            transaction2 = SentryTracer(TransactionContext("", ""), scopes)
            val collector = DefaultCompositePerformanceCollector(options)
            val timer: Timer = collector.getProperty("timer") ?: Timer(true)
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
    fun `when no collectors are set in options, collect is ignored`() {
        val collector = fixture.getSut(null, null)
        assertTrue(fixture.options.performanceCollectors.isEmpty())
        collector.start(fixture.transaction1)
        verify(fixture.mockTimer, never())!!.scheduleAtFixedRate(any(), any<Long>(), any())
    }

    @Test
    fun `collect calls collectors setup`() {
        val memoryCollector = mock<IPerformanceSnapshotCollector>()
        val cpuCollector = mock<IPerformanceSnapshotCollector>()
        val collector = fixture.getSut(memoryCollector, cpuCollector)
        collector.start(fixture.transaction1)
        Thread.sleep(300)
        verify(memoryCollector).setup()
        verify(cpuCollector).setup()
    }

    @Test
    fun `when start, timer is scheduled every 100 milliseconds`() {
        val collector = fixture.getSut()
        collector.start(fixture.transaction1)
        verify(fixture.mockTimer)!!.scheduleAtFixedRate(any(), any<Long>(), eq(100))
    }

    @Test
    fun `when start with a string, timer is scheduled every 100 milliseconds`() {
        val collector = fixture.getSut()
        collector.start(fixture.id1)
        verify(fixture.mockTimer)!!.scheduleAtFixedRate(any(), any<Long>(), eq(100))
    }

    @Test
    fun `when stop, timer is stopped`() {
        val collector = fixture.getSut()
        collector.start(fixture.transaction1)
        collector.stop(fixture.transaction1)
        verify(fixture.mockTimer)!!.scheduleAtFixedRate(any(), any<Long>(), eq(100))
        verify(fixture.mockTimer)!!.cancel()
    }

    @Test
    fun `when stop with a string, timer is stopped`() {
        val collector = fixture.getSut()
        collector.start(fixture.id1)
        collector.stop(fixture.id1)
        verify(fixture.mockTimer)!!.scheduleAtFixedRate(any(), any<Long>(), eq(100))
        verify(fixture.mockTimer)!!.cancel()
    }

    @Test
    fun `stopping a not collected transaction return null`() {
        val collector = fixture.getSut()
        val data = collector.stop(fixture.transaction1)
        verify(fixture.mockTimer, never())!!.scheduleAtFixedRate(any(), any<Long>(), eq(100))
        verify(fixture.mockTimer, never())!!.cancel()
        assertNull(data)
    }

    @Test
    fun `stopping a not collected id return null`() {
        val collector = fixture.getSut()
        val data = collector.stop(fixture.id1)
        verify(fixture.mockTimer, never())!!.scheduleAtFixedRate(any(), any<Long>(), eq(100))
        verify(fixture.mockTimer, never())!!.cancel()
        assertNull(data)
    }

    @Test
    fun `collector collect memory for multiple transactions`() {
        val collector = fixture.getSut()
        collector.start(fixture.transaction1)
        collector.start(fixture.transaction2)
        collector.start(fixture.id1)
        // Let's sleep to make the collector get values
        Thread.sleep(300)

        val data1 = collector.stop(fixture.transaction1)
        // There is still a transaction and an id running: the timer shouldn't stop now
        verify(fixture.mockTimer, never())!!.cancel()

        val data2 = collector.stop(fixture.transaction2)
        // There is still an id running: the timer shouldn't stop now
        verify(fixture.mockTimer, never())!!.cancel()

        val data3 = collector.stop(fixture.id1)
        // There are no more transactions or ids running: the time should stop now
        verify(fixture.mockTimer)!!.cancel()

        assertNotNull(data1)
        assertNotNull(data2)
        assertNotNull(data3)
        assertFalse(data1.mapNotNull { it.usedHeapMemory }.isEmpty())
        assertFalse(data1.mapNotNull { it.cpuUsagePercentage }.isEmpty())
        assertFalse(data2.mapNotNull { it.usedHeapMemory }.isEmpty())
        assertFalse(data2.mapNotNull { it.cpuUsagePercentage }.isEmpty())
        assertFalse(data3.mapNotNull { it.usedHeapMemory }.isEmpty())
        assertFalse(data3.mapNotNull { it.cpuUsagePercentage }.isEmpty())
    }

    @Test
    fun `collector times out after 30 seconds`() {
        val collector = fixture.getSut()
        collector.start(fixture.transaction1)
        // Let's sleep to make the collector get values
        Thread.sleep(300)
        verify(fixture.mockTimer, never())!!.cancel()

        // Let the timeout job stop the collector
        fixture.deferredExecutorService.runAll()
        verify(fixture.mockTimer)!!.cancel()

        // Data is deleted after the collector times out
        val data1 = collector.stop(fixture.transaction1)
        assertNull(data1)
    }

    @Test
    fun `collector has no IPerformanceCollector by default`() {
        val collector = fixture.getSut(null, null)
        assertNotNull(collector.getProperty<List<IPerformanceSnapshotCollector>>("snapshotCollectors"))
        assertTrue(collector.getProperty<List<IPerformanceSnapshotCollector>>("snapshotCollectors").isEmpty())

        assertNotNull(collector.getProperty<List<IPerformanceContinuousCollector>>("continuousCollectors"))
        assertTrue(collector.getProperty<List<IPerformanceContinuousCollector>>("continuousCollectors").isEmpty())
    }

    @Test
    fun `only one of multiple same collectors are collected`() {
        fixture.options.addPerformanceCollector(JavaMemoryCollector())
        val collector = fixture.getSut()
        // We have 2 memory collectors and 1 cpu collector
        assertEquals(3, fixture.options.performanceCollectors.size)

        collector.start(fixture.transaction1)
        // Let's sleep to make the collector get values
        Thread.sleep(300)
        val data1 = collector.stop(fixture.transaction1)
        assertNotNull(data1)
        val memoryData = data1.map { it.usedHeapMemory }
        val cpuData = data1.map { it.cpuUsagePercentage }

        // The data returned by the collector is not empty
        assertFalse(memoryData.isEmpty())
        assertFalse(cpuData.isEmpty())

        // We have the same number of memory and cpu data, even if we have 2 memory collectors and 1 cpu collector
        assertEquals(memoryData.size, cpuData.size)
    }

    @Test
    fun `setup and collect happen on background thread`() {
        val threadCheckerCollector = spy(ThreadCheckerCollector())
        fixture.options.addPerformanceCollector(threadCheckerCollector)
        val collector = fixture.getSut()
        // We have the ThreadCheckerCollector in the collectors
        assertTrue(fixture.options.performanceCollectors.any { it is ThreadCheckerCollector })

        collector.start(fixture.transaction1)
        // Let's sleep to make the collector get values
        Thread.sleep(300)
        collector.stop(fixture.transaction1)

        verify(threadCheckerCollector).setup()
        verify(threadCheckerCollector, atLeast(1)).collect(any())
    }

    @Test
    fun `when close, timer is stopped and data is cleared`() {
        val collector = fixture.getSut()
        collector.start(fixture.transaction1)
        collector.close()

        // Timer was canceled
        verify(fixture.mockTimer)!!.scheduleAtFixedRate(any(), any<Long>(), eq(100))
        verify(fixture.mockTimer)!!.cancel()

        // Data was cleared
        assertNull(collector.stop(fixture.transaction1))
    }

    @Test
    fun `start does not throw on executor shut down`() {
        val executorService = mock<ISentryExecutorService>()
        whenever(executorService.schedule(any(), any())).thenThrow(RejectedExecutionException())
        val logger = mock<ILogger>()
        fixture.options.setLogger(logger)
        fixture.options.isDebug = true
        val sut = fixture.getSut(executorService = executorService)
        sut.start(fixture.transaction1)
        verify(logger).log(eq(SentryLevel.ERROR), eq("Failed to call the executor. Performance collector will not be automatically finished. Did you call Sentry.close()?"), any())
    }

    @Test
    fun `Continuous collectors are notified properly`() {
        val collector = mock<IPerformanceContinuousCollector>()
        fixture.options.performanceCollectors.add(collector)
        val sut = fixture.getSut(memoryCollector = null, cpuCollector = null)

        // when a transaction is started
        sut.start(fixture.transaction1)

        // collector should be notified
        verify(collector).onSpanStarted(fixture.transaction1)

        // when a transaction is stopped
        sut.stop(fixture.transaction1)

        // collector should be notified
        verify(collector).onSpanFinished(fixture.transaction1)
        // and clear should be called, as there's no more running txn
        verify(collector).clear()
    }

    @Test
    fun `Continuous collectors are not called when collecting using a string id`() {
        val collector = mock<IPerformanceContinuousCollector>()
        fixture.options.performanceCollectors.add(collector)
        val sut = fixture.getSut(memoryCollector = null, cpuCollector = null)

        // when a collection is started with an id
        sut.start(fixture.id1)

        // collector should not be notified
        verify(collector, never()).onSpanStarted(fixture.transaction1)

        // when the id collection is stopped
        sut.stop(fixture.id1)

        // collector should not be notified
        verify(collector, never()).onSpanFinished(fixture.transaction1)

        verify(collector).clear()
    }

    @Test
    fun `Continuous collectors are notified properly even when multiple txn are running`() {
        val collector = mock<IPerformanceContinuousCollector>()
        fixture.options.performanceCollectors.add(collector)
        val sut = fixture.getSut(memoryCollector = null, cpuCollector = null)

        // when a transaction is started
        sut.start(fixture.transaction1)

        // collector should be notified
        verify(collector).onSpanStarted(fixture.transaction1)

        // when a second transaction is started
        sut.start(fixture.transaction2)

        // collector should be notified again
        verify(collector).onSpanStarted(fixture.transaction2)

        // when the first transaction is stopped
        sut.stop(fixture.transaction1)

        // collector should be notified
        verify(collector).onSpanFinished(fixture.transaction1)

        // but clear should not be called, as there's still txn 2 running
        verify(collector, never()).clear()

        // unless the txn finishes as well
        sut.stop(fixture.transaction2)

        verify(collector).onSpanFinished(fixture.transaction2)
        verify(collector).clear()
    }

    @Test
    fun `span start and finishes are propagated as well`() {
        val collector = mock<IPerformanceContinuousCollector>()
        fixture.options.performanceCollectors.add(collector)
        val sut = fixture.getSut(memoryCollector = null, cpuCollector = null)

        val span = mock<ISpan>()

        // when a transaction is started
        sut.start(fixture.transaction1)
        sut.onSpanStarted(span)
        sut.onSpanFinished(span)
        sut.stop(fixture.transaction1)

        verify(collector).onSpanStarted(fixture.transaction1)
        verify(collector).onSpanStarted(span)
        verify(collector).onSpanFinished(span)
        verify(collector).onSpanFinished(fixture.transaction1)
        verify(collector).clear()
    }

    inner class ThreadCheckerCollector :
        IPerformanceSnapshotCollector {
        override fun setup() {
            if (threadChecker.isMainThread) {
                throw AssertionError("setup() was called in the main thread")
            }
        }

        override fun collect(performanceCollectionData: PerformanceCollectionData) {
            if (threadChecker.isMainThread) {
                throw AssertionError("collect() was called in the main thread")
            }
        }
    }
}
