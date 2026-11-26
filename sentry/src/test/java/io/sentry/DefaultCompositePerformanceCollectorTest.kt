package io.sentry

import io.sentry.test.getCtor
import io.sentry.test.getProperty
import io.sentry.util.thread.ThreadChecker
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
    val executorService = FakeTimerExecutorService()

    val mockCpuCollector: IPerformanceSnapshotCollector =
      object : IPerformanceSnapshotCollector {
        override fun setup() {}

        override fun collect(performanceCollectionData: PerformanceCollectionData) {
          performanceCollectionData.cpuUsagePercentage = 1.0
        }
      }

    init {
      whenever(scopes.options).thenReturn(options)
      options.setTimerExecutorService(executorService)
    }

    fun getSut(
      memoryCollector: IPerformanceSnapshotCollector? = JavaMemoryCollector(),
      cpuCollector: IPerformanceSnapshotCollector? = mockCpuCollector,
      optionsConfiguration: Sentry.OptionsConfiguration<SentryOptions> =
        Sentry.OptionsConfiguration {},
    ): CompositePerformanceCollector {
      options.dsn = "https://key@sentry.io/proj"
      if (cpuCollector != null) {
        options.addPerformanceCollector(cpuCollector)
      }
      if (memoryCollector != null) {
        options.addPerformanceCollector(memoryCollector)
      }
      optionsConfiguration.configure(options)
      transaction1 = SentryTracer(TransactionContext("", ""), scopes)
      transaction2 = SentryTracer(TransactionContext("", ""), scopes)
      return DefaultCompositePerformanceCollector(options)
    }
  }

  /**
   * Runs scheduled tasks on the calling thread, only when the test advances its clock, so tests can
   * step through each collection and nothing keeps running after a test ends.
   */
  private class FakeTimerExecutorService : ISentryExecutorService {
    private class ScheduledTask(val runAtMillis: Long, runnable: Runnable) :
      FutureTask<Unit>(runnable, Unit)

    var nowMillis = 0L
      private set

    private val tasks = mutableListOf<ScheduledTask>()
    val scheduledDelays = mutableListOf<Long>()

    override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
      scheduledDelays.add(delayMillis)
      return ScheduledTask(nowMillis + delayMillis, runnable).also { tasks.add(it) }
    }

    /** Runs, in order, every task due within [millis], including tasks those tasks schedule. */
    fun advanceBy(millis: Long) {
      val targetMillis = nowMillis + millis
      while (true) {
        val next =
          tasks
            .filterNot { it.isCancelled }
            .minByOrNull { it.runAtMillis }
            ?.takeIf {
              it.runAtMillis <= targetMillis
            } ?: break
        tasks.remove(next)
        nowMillis = maxOf(nowMillis, next.runAtMillis)
        next.run()
      }
      nowMillis = maxOf(nowMillis, targetMillis)
    }

    /** Called from a running task to simulate it holding the timer thread for [millis]. */
    fun busyFor(millis: Long) {
      nowMillis += millis
    }

    override fun submit(runnable: Runnable): Future<*> = throw UnsupportedOperationException()

    override fun <T> submit(callable: Callable<T>): Future<T> =
      throw UnsupportedOperationException()

    override fun close(timeoutMillis: Long) {}

    override fun isClosed(): Boolean = false
  }

  private fun CompositePerformanceCollector.collectFuture(): Future<*>? =
    getProperty<Future<*>?>("collectFuture")

  @Test
  fun `when null param is provided, invalid argument is thrown`() {
    val ctor = className.getCtor(ctorTypes)

    assertFailsWith<IllegalArgumentException> { ctor.newInstance(arrayOf<Any?>(null)) }
  }

  @Test
  fun `when no collectors are set in options, collect is ignored`() {
    val collector = fixture.getSut(null, null)
    assertTrue(fixture.options.performanceCollectors.isEmpty())
    collector.start(fixture.transaction1)
    assertTrue(fixture.executorService.scheduledDelays.isEmpty())
  }

  @Test
  fun `collect calls collectors setup`() {
    val memoryCollector = mock<IPerformanceSnapshotCollector>()
    val cpuCollector = mock<IPerformanceSnapshotCollector>()
    val collector = fixture.getSut(memoryCollector, cpuCollector)
    collector.start(fixture.transaction1)
    fixture.executorService.advanceBy(0)
    verify(memoryCollector).setup()
    verify(cpuCollector).setup()
  }

  @Test
  fun `when start, collection runs every 100 milliseconds`() {
    val collector = fixture.getSut(memoryCollector = null, cpuCollector = countingCollector)
    collector.start(fixture.transaction1)

    assertCollectsEvery100Millis()
  }

  @Test
  fun `when start with a string, collection runs every 100 milliseconds`() {
    val collector = fixture.getSut(memoryCollector = null, cpuCollector = countingCollector)
    collector.start(fixture.id1)

    assertCollectsEvery100Millis()
  }

  private var collectCount = 0
  private val countingCollector =
    object : IPerformanceSnapshotCollector {
      override fun setup() {}

      override fun collect(performanceCollectionData: PerformanceCollectionData) {
        collectCount++
      }
    }

  private fun assertCollectsEvery100Millis() {
    fixture.executorService.advanceBy(99)
    assertEquals(0, collectCount)
    fixture.executorService.advanceBy(1)
    assertEquals(1, collectCount)
    fixture.executorService.advanceBy(100)
    assertEquals(2, collectCount)
    fixture.executorService.advanceBy(100)
    assertEquals(3, collectCount)
  }

  @Test
  fun `when stop, collection is stopped`() {
    val collector = fixture.getSut()
    collector.start(fixture.transaction1)
    assertNotNull(collector.collectFuture())
    collector.stop(fixture.transaction1)
    assertNull(collector.collectFuture())
  }

  @Test
  fun `when stop with a string, collection is stopped`() {
    val collector = fixture.getSut()
    collector.start(fixture.id1)
    assertNotNull(collector.collectFuture())
    collector.stop(fixture.id1)
    assertNull(collector.collectFuture())
  }

  @Test
  fun `stopping a not collected transaction return null`() {
    val collector = fixture.getSut()
    val data = collector.stop(fixture.transaction1)
    assertTrue(fixture.executorService.scheduledDelays.isEmpty())
    assertNull(collector.collectFuture())
    assertNull(data)
  }

  @Test
  fun `stopping a not collected id return null`() {
    val collector = fixture.getSut()
    val data = collector.stop(fixture.id1)
    assertTrue(fixture.executorService.scheduledDelays.isEmpty())
    assertNull(collector.collectFuture())
    assertNull(data)
  }

  @Test
  fun `collector collect memory for multiple transactions`() {
    val collector = fixture.getSut()
    collector.start(fixture.transaction1)
    collector.start(fixture.transaction2)
    collector.start(fixture.id1)
    fixture.executorService.advanceBy(300)

    val data1 = collector.stop(fixture.transaction1)
    // There is still a transaction and an id running: the collection shouldn't stop now
    assertNotNull(collector.collectFuture())

    val data2 = collector.stop(fixture.transaction2)
    // There is still an id running: the collection shouldn't stop now
    assertNotNull(collector.collectFuture())

    val data3 = collector.stop(fixture.id1)
    // There are no more transactions or ids running: the collection should stop now
    assertNull(collector.collectFuture())

    assertNotNull(data1)
    assertNotNull(data2)
    assertNotNull(data3)
    assertTrue(data1.any { it.hasUsedHeapMemory() })
    assertTrue(data1.any { it.hasCpuUsagePercentage() })
    assertTrue(data2.any { it.hasUsedHeapMemory() })
    assertTrue(data2.any { it.hasCpuUsagePercentage() })
    assertTrue(data3.any { it.hasUsedHeapMemory() })
    assertTrue(data3.any { it.hasCpuUsagePercentage() })
  }

  @Test
  fun `collector times out after 30 seconds`() {
    val mockDateProvider = mock<SentryDateProvider>()
    val mockCollector = mock<IPerformanceContinuousCollector>()
    val dates =
      listOf(
        SentryNanotimeDate(TimeUnit.SECONDS.toMillis(100), TimeUnit.SECONDS.toNanos(100)),
        SentryNanotimeDate(TimeUnit.SECONDS.toMillis(131), TimeUnit.SECONDS.toNanos(131)),
      )
    whenever(mockDateProvider.now()).thenReturn(dates[0], dates[0], dates[0], dates[1])
    val collector = fixture.getSut {
      it.dateProvider = mockDateProvider
      it.addPerformanceCollector(mockCollector)
    }
    collector.start(fixture.transaction1)
    assertNotNull(collector.collectFuture())

    fixture.executorService.advanceBy(300)

    // When the collector gets the values, it checks the current date, set 31 seconds after the
    // begin. This means it should stop itself
    assertNull(collector.collectFuture())

    // When the collector times out, the data collection for spans is stopped, too
    verify(mockCollector).onSpanFinished(eq(fixture.transaction1))

    // Data is deleted after the collector times out
    val data1 = collector.stop(fixture.transaction1)
    assertNull(data1)
  }

  @Test
  fun `collector collects for 30 seconds`() {
    val mockDateProvider = mock<SentryDateProvider>()
    val dates =
      listOf(
        SentryNanotimeDate(TimeUnit.SECONDS.toMillis(100), TimeUnit.SECONDS.toNanos(100)),
        SentryNanotimeDate(TimeUnit.SECONDS.toMillis(130), TimeUnit.SECONDS.toNanos(130)),
      )
    whenever(mockDateProvider.now()).thenReturn(dates[0], dates[0], dates[0], dates[1])
    val collector = fixture.getSut { it.dateProvider = mockDateProvider }
    collector.start(fixture.transaction1)
    assertNotNull(collector.collectFuture())

    fixture.executorService.advanceBy(300)

    // When the collector gets the values, it checks the current date, set 30 seconds after the
    // begin. This means it should continue without being cancelled
    assertNotNull(collector.collectFuture())

    // Data is deleted after the collector times out
    val data1 = collector.stop(fixture.transaction1)
    assertNotNull(data1)
  }

  @Test
  fun `collector has no IPerformanceCollector by default`() {
    val collector = fixture.getSut(null, null)
    assertNotNull(collector.getProperty<List<IPerformanceSnapshotCollector>>("snapshotCollectors"))
    assertTrue(
      collector.getProperty<List<IPerformanceSnapshotCollector>>("snapshotCollectors").isEmpty(),
    )

    assertNotNull(
      collector.getProperty<List<IPerformanceContinuousCollector>>("continuousCollectors"),
    )
    assertTrue(
      collector
        .getProperty<List<IPerformanceContinuousCollector>>("continuousCollectors")
        .isEmpty(),
    )
  }

  @Test
  fun `only one of multiple same collectors are collected`() {
    fixture.options.addPerformanceCollector(JavaMemoryCollector())
    val collector = fixture.getSut()
    // We have 2 memory collectors and 1 cpu collector
    assertEquals(3, fixture.options.performanceCollectors.size)

    collector.start(fixture.transaction1)
    fixture.executorService.advanceBy(300)
    val data1 = collector.stop(fixture.transaction1)
    assertNotNull(data1)
    val memoryData = data1.filter { it.hasUsedHeapMemory() }
    val cpuData = data1.filter { it.hasCpuUsagePercentage() }

    // The data returned by the collector is not empty
    assertFalse(memoryData.isEmpty())
    assertFalse(cpuData.isEmpty())

    // We have the same number of memory and cpu data, even if we have 2 memory collectors and 1 cpu
    // collector
    assertEquals(memoryData.size, cpuData.size)
  }

  @Test
  fun `first collect runs a full interval after setup even when the timer thread was busy`() {
    var setupMillis = -1L
    var firstCollectMillis = -1L
    val timingCollector =
      object : IPerformanceSnapshotCollector {
        override fun setup() {
          setupMillis = fixture.executorService.nowMillis
        }

        override fun collect(performanceCollectionData: PerformanceCollectionData) {
          if (firstCollectMillis == -1L) {
            firstCollectMillis = fixture.executorService.nowMillis
          }
        }
      }
    val collector = fixture.getSut(memoryCollector = null, cpuCollector = timingCollector)
    fixture.executorService.schedule({ fixture.executorService.busyFor(200) }, 0L)

    collector.start(fixture.transaction1)
    fixture.executorService.advanceBy(300)

    assertEquals(200, setupMillis)
    assertEquals(300, firstCollectMillis)
  }

  @Test
  fun `setup and collect happen on background thread`() {
    val executorService = SentryExecutorService(fixture.options)
    fixture.options.setTimerExecutorService(executorService)
    val threadCheckerCollector = spy(ThreadCheckerCollector())
    fixture.options.addPerformanceCollector(threadCheckerCollector)
    val collector = fixture.getSut()
    // We have the ThreadCheckerCollector in the collectors
    assertTrue(fixture.options.performanceCollectors.any { it is ThreadCheckerCollector })

    collector.start(fixture.transaction1)
    // Let's sleep to make the collector get values
    Thread.sleep(300)
    collector.stop(fixture.transaction1)
    executorService.close(0)

    verify(threadCheckerCollector).setup()
    verify(threadCheckerCollector, atLeast(1)).collect(any())
  }

  @Test
  fun `when close, collection is stopped and data is cleared`() {
    val collector = fixture.getSut()
    collector.start(fixture.transaction1)
    collector.close()

    // Collection was canceled
    assertTrue(fixture.executorService.scheduledDelays.isNotEmpty())
    assertNull(collector.collectFuture())

    // Data was cleared
    assertNull(collector.stop(fixture.transaction1))
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

  inner class ThreadCheckerCollector : IPerformanceSnapshotCollector {
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
