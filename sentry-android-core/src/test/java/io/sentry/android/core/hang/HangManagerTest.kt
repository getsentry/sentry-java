package io.sentry.android.core.hang

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.AppState
import io.sentry.android.core.AppStateProvider
import io.sentry.android.core.anr.AnrStackTrace
import io.sentry.android.core.hang.HangManager.DEFAULT_MAX_RECORDING_TIME_MS
import io.sentry.android.core.hang.HangManager.DEFAULT_POLLING_INTERVAL_MS
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import io.sentry.test.NonOverridableNoOpSentryExecutorService
import io.sentry.time.EpochClock
import io.sentry.time.JavaMonotonicTicker
import io.sentry.time.TestMonotonicTicker
import io.sentry.time.Timestamp
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.locks.LockSupport
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class HangManagerTest {

  private lateinit var ticker: TestMonotonicTicker
  private var unixTimeMs = 1_700_000_000_000L
  private lateinit var logger: ILogger
  private lateinit var sdkExecutor: DeferredExecutorService
  private lateinit var options: SentryOptions
  private val epochClock = EpochClock { Timestamp.ofEpochNanos(MILLISECONDS.toNanos(unixTimeMs)) }
  private lateinit var appStateProvider: FakeAppStateProvider
  private val executors = mutableListOf<FakeScheduledExecutorService>()
  private lateinit var manager: HangManager
  private lateinit var signal: FakeSignal
  private val signals = mutableListOf<FakeSignal>()
  private lateinit var collector: FakeCollector
  private lateinit var store: FakeStore
  private val records = mutableListOf<HangRecord>()
  private val listener = FakeListener(records)
  private val record = HangRecordingRequest(true, false)
  private val persist = HangRecordingRequest(true, true)

  private val executor: FakeScheduledExecutorService
    get() = executors.last()

  @BeforeTest
  fun setUp() {
    // A tick's origin is arbitrary, so start far from zero.
    ticker = TestMonotonicTicker(HOURS.toNanos(30))
    logger = mock()
    sdkExecutor = DeferredExecutorService()
    options = SentryOptions()
    options.setLogger(logger)
    options.isDebug = true
    options.executorService = sdkExecutor
    appStateProvider = FakeAppStateProvider()
    collector = FakeCollector()
    store = FakeStore()
    createRecorder()
  }

  private fun createRecorder(
    signalCount: Int = 1,
    collectors: List<HangCollectorSetup<*>> = listOf(setup(collector, store)),
    maxSamples: Int = MAX_SAMPLES,
  ) {
    manager =
      HangManager(
        options,
        appStateProvider,
        ticker,
        epochClock,
        DEFAULT_POLLING_INTERVAL_MS,
        DEFAULT_MAX_RECORDING_TIME_MS,
        maxSamples,
        { FakeScheduledExecutorService().also { executors.add(it) } },
        { metronome ->
          signals.clear()
          repeat(signalCount) { signals.add(FakeSignal(metronome)) }
          signals
        },
        collectors,
      )
    signal = signals.first()
  }

  private fun setup(collector: FakeCollector, store: FakeStore?) =
    HangCollectorSetup(collector, writer, store)

  private fun recordedValues(): List<List<String>> {
    sdkExecutor.runAll()
    return records.map { record -> record.stackTraces.map { it.stack.single().methodName } }
  }

  private fun nowMs(): Long = TimeUnit.NANOSECONDS.toMillis(ticker.tickNanos())

  private fun advance(ms: Long) {
    ticker.advance(ms, MILLISECONDS)
    unixTimeMs += ms
  }

  private fun startInForeground() {
    manager.subscribe(listener)
    executor.runPending()
    appStateProvider.foreground()
    executor.runPending()
  }

  private fun openHangInTick(durationMs: Long) {
    signal.onNextTick { signal.report(HangSignal.State.SUSPICIOUS, durationMs) }
    executor.runTick()
  }

  private fun tickEvery66Ms(count: Int) {
    repeat(count) {
      advance(DEFAULT_POLLING_INTERVAL_MS)
      executor.runTick()
    }
  }

  private fun tickUntilRecorded() {
    var ticks = 0
    while (
      records.isEmpty() && ticks < DEFAULT_MAX_RECORDING_TIME_MS / DEFAULT_POLLING_INTERVAL_MS + 2
    ) {
      advance(DEFAULT_POLLING_INTERVAL_MS)
      executor.runTick()
      sdkExecutor.runAll()
      ticks++
    }
    assertThat(records).isNotEmpty()
  }

  @Test
  fun `suspicious starts a record, asks the subscriber and samples in the same tick`() {
    startInForeground()

    openHangInTick(300)

    assertThat(listener.started).isEqualTo(1)
    assertThat(collector.calls).containsExactly("sample")
  }

  @Test
  fun `each subscriber is asked once at the start of a hang`() {
    val other = FakeListener()
    manager.subscribe(other)
    startInForeground()

    openHangInTick(300)
    executor.runTick()

    assertThat(listener.started).isEqualTo(1)
    assertThat(other.started).isEqualTo(1)
  }

  @Test
  fun `subscriber added during a hang is not asked and gets no record`() {
    startInForeground()
    openHangInTick(300)
    val late = FakeListener(request = persist)

    manager.subscribe(late)
    executor.runPending()
    tickEvery66Ms(15)
    signal.report(HangSignal.State.IDLE, 0)
    sdkExecutor.runAll()

    assertThat(late.started).isEqualTo(0)
    assertThat(late.records).isEmpty()
    assertThat(store.calls).isEmpty()
  }

  @Test
  fun `ticks with a fixed delay of the tick interval`() {
    startInForeground()

    assertThat(executor.activePeriodicTasks.map { it.delayMs })
      .containsExactly(DEFAULT_POLLING_INTERVAL_MS)
  }

  @Test
  fun `tick listeners run before collectors sample`() {
    val calls = collector.calls
    manager.addTickListener { calls.add("tick") }
    startInForeground()

    openHangInTick(300)
    executor.runTick()

    assertThat(calls).containsExactly("tick", "sample", "tick", "sample").inOrder()
  }

  @Test
  fun `idle ends the hang and notifies listeners on sdk executor`() {
    startInForeground()
    val suspiciousSinceMs = nowMs() - 300
    val timestampAtOpenMs = unixTimeMs
    openHangInTick(300)
    advance(1_000)
    val idleSinceMs = nowMs() - 20

    signal.report(HangSignal.State.IDLE, 20)

    assertThat(records).isEmpty()
    sdkExecutor.runAll()
    val record = records.single()
    assertThat(record.durationMs).isEqualTo(idleSinceMs - suspiciousSinceMs)
    assertThat(record.startTimestampMs).isEqualTo(timestampAtOpenMs - 300)
    assertThat(recordedValues().single()).containsExactly("s1")
  }

  @Test
  fun `maxRecordingTime counts from the monotonic start of the hang`() {
    startInForeground()
    openHangInTick(300)

    advance(DEFAULT_MAX_RECORDING_TIME_MS - 300 - 1)
    executor.runTick()
    sdkExecutor.runAll()
    assertThat(records).isEmpty()

    advance(1)
    executor.runTick()
    sdkExecutor.runAll()
    assertThat(records.single().durationMs).isEqualTo(DEFAULT_MAX_RECORDING_TIME_MS)
  }

  @Test
  fun `the writer puts the non-null values of the hang into the record in order`() {
    startInForeground()
    openHangInTick(300)
    collector.returnNull = true
    executor.runTick()
    collector.returnNull = false
    executor.runTick()

    signal.report(HangSignal.State.IDLE, 0)

    assertThat(recordedValues().single()).containsExactly("s1", "s3").inOrder()
  }

  @Test
  fun `a new hang starts with an empty record`() {
    startInForeground()
    openHangInTick(300)
    signal.report(HangSignal.State.IDLE, 0)

    openHangInTick(300)
    signal.report(HangSignal.State.IDLE, 0)

    assertThat(recordedValues()).containsExactly(listOf("s1"), listOf("s2")).inOrder()
  }

  @Test
  fun `collectors sample on exactly maxSamples ticks`() {
    createRecorder(maxSamples = 3)
    startInForeground()
    openHangInTick(300)

    tickEvery66Ms(5)
    signal.report(HangSignal.State.IDLE, 0)

    assertThat(collector.calls).hasSize(3)
    assertThat(recordedValues().single()).containsExactly("s1", "s2", "s3").inOrder()
  }

  @Test
  fun `a sampling tick without a value counts towards maxSamples`() {
    createRecorder(maxSamples = 2)
    startInForeground()
    collector.returnNull = true
    openHangInTick(300)
    collector.returnNull = false

    tickEvery66Ms(5)
    signal.report(HangSignal.State.IDLE, 0)

    assertThat(recordedValues().single()).containsExactly("s2")
  }

  @Test
  fun `the maxRecordingTime cut still happens after maxSamples`() {
    createRecorder(maxSamples = 3)
    startInForeground()
    openHangInTick(0)

    tickUntilRecorded()

    assertThat(records.single().durationMs).isEqualTo(DEFAULT_MAX_RECORDING_TIME_MS)
    assertThat(records.single().hasEndedAtMaxRecordingTime()).isTrue()
  }

  @Test
  fun `the writers run in setup order on each tick`() {
    val second = FakeCollector("t")
    createRecorder(collectors = listOf(setup(collector, null), setup(second, null)))
    startInForeground()
    openHangInTick(300)
    executor.runTick()

    signal.report(HangSignal.State.IDLE, 0)

    assertThat(recordedValues().single()).containsExactly("s1", "t1", "s2", "t2").inOrder()
  }

  @Test
  fun `values stay in memory when no subscriber asks to persist`() {
    startInForeground()
    openHangInTick(0)

    tickEvery66Ms(20)

    assertThat(store.calls).isEmpty()
  }

  @Test
  fun `persisting hang clears the store at its start and then writes each value`() {
    listener.request = persist
    startInForeground()

    openHangInTick(300)
    assertThat(store.calls).containsExactly("clear", "add s1").inOrder()

    executor.runTick()
    assertThat(store.calls).containsExactly("clear", "add s1", "add s2").inOrder()
  }

  @Test
  fun `persisting hang clears each store once`() {
    val second = FakeCollector("t")
    val secondStore = FakeStore()
    listener.request = persist
    createRecorder(collectors = listOf(setup(collector, store), setup(second, secondStore)))
    startInForeground()

    openHangInTick(300)
    tickEvery66Ms(3)

    assertThat(store.calls.count { it == "clear" }).isEqualTo(1)
    assertThat(secondStore.calls.first()).isEqualTo("clear")
    assertThat(secondStore.calls.count { it == "clear" }).isEqualTo(1)
  }

  @Test
  fun `one persisting subscriber before one that does not persist is enough`() {
    manager.subscribe(FakeListener(request = persist))
    startInForeground()

    openHangInTick(0)

    assertThat(store.calls).contains("clear")
  }

  @Test
  fun `one persisting subscriber after one that does not persist is enough`() {
    startInForeground()
    manager.subscribe(FakeListener(request = persist))
    executor.runPending()

    openHangInTick(0)

    assertThat(store.calls).contains("clear")
  }

  @Test
  fun `persisting hang stops writing at maxSamples`() {
    listener.request = persist
    createRecorder(maxSamples = 12)
    startInForeground()
    openHangInTick(0)

    tickEvery66Ms(20)

    assertThat(store.calls.count { it.startsWith("add") }).isEqualTo(12)
  }

  @Test
  fun `collector without a store is sampled but not persisted`() {
    listener.request = persist
    createRecorder(collectors = listOf(setup(collector, null)))
    startInForeground()
    openHangInTick(0)

    tickEvery66Ms(15)
    signal.report(HangSignal.State.IDLE, 0)

    assertThat(recordedValues().single()).hasSize(16)
  }

  @Test
  fun `each persisted hang clears the store once`() {
    listener.request = persist
    startInForeground()
    openHangInTick(0)
    tickEvery66Ms(3)
    signal.report(HangSignal.State.IDLE, 0)
    store.calls.clear()

    openHangInTick(0)
    tickEvery66Ms(3)

    assertThat(store.calls.first()).isEqualTo("clear")
    assertThat(store.calls.count { it == "clear" }).isEqualTo(1)
  }

  @Test
  fun `the persist decision is fixed for the hang`() {
    startInForeground()
    openHangInTick(0)

    listener.request = persist
    tickEvery66Ms(15)

    assertThat(store.calls).isEmpty()
  }

  @Test
  fun `store that fails on add is not written again in this hang`() {
    listener.request = persist
    store.throwOnAdd = true
    startInForeground()
    openHangInTick(0)
    store.throwOnAdd = false

    tickEvery66Ms(5)

    assertThat(store.calls).containsExactly("clear", "add s1").inOrder()
    verify(logger, times(1))
      .log(eq(SentryLevel.WARNING), eq("Failed to write to a hang sample store"), any<Throwable>())
  }

  @Test
  fun `store that fails on add does not stop the other collectors in the same tick`() {
    val second = FakeCollector("t")
    val secondStore = FakeStore()
    listener.request = persist
    store.throwOnAdd = true
    createRecorder(collectors = listOf(setup(collector, store), setup(second, secondStore)))
    startInForeground()

    openHangInTick(0)
    signal.report(HangSignal.State.IDLE, 0)

    assertThat(recordedValues().single()).containsExactly("s1", "t1").inOrder()
    assertThat(secondStore.calls).containsExactly("clear")
  }

  @Test
  fun `store that fails on clear is not written in this hang`() {
    listener.request = persist
    store.throwOnClear = true
    startInForeground()
    openHangInTick(0)

    tickEvery66Ms(5)
    signal.report(HangSignal.State.IDLE, 0)

    assertThat(store.calls).containsExactly("clear")
    assertThat(recordedValues().single()).hasSize(6)
    verify(logger)
      .log(eq(SentryLevel.WARNING), eq("Failed to write to a hang sample store"), any<Throwable>())
  }

  @Test
  fun `store that failed is written again in the next hang`() {
    listener.request = persist
    store.throwOnClear = true
    startInForeground()
    openHangInTick(0)
    signal.report(HangSignal.State.IDLE, 0)
    store.throwOnClear = false
    store.calls.clear()

    openHangInTick(0)
    tickEvery66Ms(2)

    assertThat(store.calls).containsExactly("clear", "add s2", "add s3", "add s4").inOrder()
  }

  @Test
  fun `cancel does not touch the store`() {
    listener.request = persist
    startInForeground()
    openHangInTick(0)
    tickEvery66Ms(3)
    val callsBeforeCancel = store.calls.toList()

    signal.cancel()
    executor.runTick()

    assertThat(store.calls).isEqualTo(callsBeforeCancel)
  }

  @Test
  fun `background end of a persisting hang stops the store writes`() {
    listener.request = persist
    startInForeground()
    openHangInTick(0)
    tickEvery66Ms(3)
    val callsBeforeEnd = store.calls.toList()

    appStateProvider.background()
    executor.runPending()
    tickEvery66Ms(3)

    assertThat(store.calls).isEqualTo(callsBeforeEnd)
    assertThat(recordedValues().single()).hasSize(4)
  }

  @Test
  fun `last unsubscribe during a persisting hang stops the store writes`() {
    listener.request = persist
    startInForeground()
    openHangInTick(0)
    tickEvery66Ms(3)
    val callsBeforeUnsubscribe = store.calls.toList()

    manager.unsubscribe(listener)
    executor.runPending()
    tickEvery66Ms(3)

    assertThat(store.calls).isEqualTo(callsBeforeUnsubscribe)
  }

  @Test
  fun `maxRecordingTime cut of a persisting hang stops the store writes`() {
    listener.request = persist
    startInForeground()
    openHangInTick(0)
    tickUntilRecorded()
    val callsAtCut = store.calls.toList()

    tickEvery66Ms(3)
    signal.report(HangSignal.State.IDLE, 0)
    tickEvery66Ms(3)

    assertThat(store.calls).isEqualTo(callsAtCut)
    assertThat(store.calls.count { it == "clear" }).isEqualTo(1)
  }

  @Test
  fun `hang without persist after a persisted one does not clear the store`() {
    listener.request = persist
    startInForeground()
    openHangInTick(0)
    tickEvery66Ms(3)
    signal.report(HangSignal.State.IDLE, 0)
    listener.request = record
    store.calls.clear()

    openHangInTick(0)
    tickEvery66Ms(15)

    assertThat(store.calls).isEmpty()
  }

  @Test
  fun `subscribers that need nothing cause no sample, no store call and no record`() {
    listener.request = null
    startInForeground()
    openHangInTick(0)
    tickEvery66Ms(15)

    signal.report(HangSignal.State.IDLE, 0)
    sdkExecutor.runAll()

    assertThat(listener.started).isEqualTo(1)
    assertThat(collector.calls).isEmpty()
    assertThat(store.calls).isEmpty()
    assertThat(records).isEmpty()
  }

  @Test
  fun `a request to persist without a record does nothing`() {
    listener.request = HangRecordingRequest(false, true)
    startInForeground()
    openHangInTick(0)
    tickEvery66Ms(15)

    signal.report(HangSignal.State.IDLE, 0)
    sdkExecutor.runAll()

    assertThat(collector.calls).isEmpty()
    assertThat(store.calls).isEmpty()
    assertThat(records).isEmpty()
  }

  @Test
  fun `record goes only to the subscribers that record`() {
    val notRecording = FakeListener(request = null)
    manager.subscribe(notRecording)
    startInForeground()
    openHangInTick(300)

    signal.report(HangSignal.State.IDLE, 0)

    assertThat(recordedValues().single()).containsExactly("s1")
    assertThat(notRecording.records).isEmpty()
    assertThat(notRecording.started).isEqualTo(1)
  }

  @Test
  fun `record does not go to a subscriber that left during the hang`() {
    val leaving = FakeListener()
    manager.subscribe(leaving)
    startInForeground()
    openHangInTick(300)

    manager.unsubscribe(leaving)
    executor.runPending()
    signal.report(HangSignal.State.IDLE, 0)
    sdkExecutor.runAll()

    assertThat(records).hasSize(1)
    assertThat(leaving.records).isEmpty()
  }

  @Test
  fun `record goes to the subscribers of the hang end, also if they leave before the delivery`() {
    val other = FakeListener()
    manager.subscribe(other)
    startInForeground()
    openHangInTick(300)
    signal.report(HangSignal.State.IDLE, 0)

    manager.unsubscribe(other)
    executor.runPending()
    sdkExecutor.runAll()

    assertThat(records).hasSize(1)
    assertThat(other.records).hasSize(1)
  }

  @Test
  fun `no sample after idle`() {
    startInForeground()
    openHangInTick(300)
    signal.report(HangSignal.State.IDLE, 0)
    collector.calls.clear()

    executor.runTick()

    assertThat(collector.calls).isEmpty()
  }

  @Test
  fun `idle without an open hang is ignored`() {
    startInForeground()

    signal.report(HangSignal.State.IDLE, 0)
    executor.runTick()
    sdkExecutor.runAll()

    assertThat(collector.calls).isEmpty()
    assertThat(records).isEmpty()
  }

  @Test
  fun `second suspicious from another signal does not start a second record`() {
    createRecorder(signalCount = 2)
    val otherSignal = signals[1]
    startInForeground()
    openHangInTick(300)
    advance(100)

    otherSignal.report(HangSignal.State.SUSPICIOUS, 0)
    advance(100)
    signal.report(HangSignal.State.IDLE, 0)
    sdkExecutor.runAll()

    assertThat(listener.started).isEqualTo(1)
    assertThat(records.single().durationMs).isEqualTo(500)
  }

  @Test
  fun `signal factory gets the manager as metronome`() {
    var metronome: Metronome? = null
    val created =
      HangManager(
        options,
        appStateProvider,
        ticker,
        epochClock,
        DEFAULT_POLLING_INTERVAL_MS,
        DEFAULT_MAX_RECORDING_TIME_MS,
        MAX_SAMPLES,
        { FakeScheduledExecutorService() },
        {
          metronome = it
          emptyList()
        },
        emptyList(),
      )

    assertThat(metronome).isSameInstanceAs(created)
  }

  @Test
  fun `default executor factory runs the manager on a daemon thread`() {
    val executor = HangManager.DEFAULT_EXECUTOR_FACTORY.create()
    try {
      val thread = executor.submit<Thread> { Thread.currentThread() }.get(10, TimeUnit.SECONDS)

      assertThat(thread.name).isEqualTo("SentryHangManager")
      assertThat(thread.isDaemon).isTrue()
    } finally {
      executor.shutdown()
    }
  }

  @Test
  fun `maxRecordingTime ends the hang with durationMs 8000 and later idle is ignored`() {
    startInForeground()
    openHangInTick(0)
    tickUntilRecorded()
    val samplesAtEnd = collector.calls.size

    executor.runTick()
    signal.report(HangSignal.State.IDLE, 0)
    executor.runTick()
    sdkExecutor.runAll()

    assertThat(records.single().durationMs).isEqualTo(DEFAULT_MAX_RECORDING_TIME_MS)
    assertThat(collector.calls).hasSize(samplesAtEnd)
  }

  @Test
  fun `maxRecordingTime end marks the record as ended at maxRecordingTime`() {
    startInForeground()
    openHangInTick(0)

    tickUntilRecorded()

    assertThat(records.single().hasEndedAtMaxRecordingTime()).isTrue()
  }

  @Test
  fun `idle end does not mark the record as ended at maxRecordingTime`() {
    startInForeground()
    openHangInTick(300)
    advance(500)

    signal.report(HangSignal.State.IDLE, 0)
    sdkExecutor.runAll()

    assertThat(records.single().hasEndedAtMaxRecordingTime()).isFalse()
  }

  @Test
  fun `background end does not mark the record as ended at maxRecordingTime`() {
    startInForeground()
    openHangInTick(300)
    advance(500)

    appStateProvider.background()
    executor.runPending()
    sdkExecutor.runAll()

    assertThat(records.single().hasEndedAtMaxRecordingTime()).isFalse()
  }

  @Test
  fun `collector that always throws does not stop the maxRecordingTime end`() {
    startInForeground()
    collector.throwOnSample = true
    openHangInTick(0)

    tickUntilRecorded()

    assertThat(records.single().durationMs).isEqualTo(DEFAULT_MAX_RECORDING_TIME_MS)
    assertThat(records.single().hasEndedAtMaxRecordingTime()).isTrue()
  }

  @Test
  fun `after maxRecordingTime no hang starts until idle`() {
    startInForeground()
    openHangInTick(0)
    advance(DEFAULT_MAX_RECORDING_TIME_MS)
    executor.runTick()
    listener.started = 0

    signal.report(HangSignal.State.SUSPICIOUS, 0)
    assertThat(listener.started).isEqualTo(0)

    signal.report(HangSignal.State.IDLE, 0)
    signal.report(HangSignal.State.SUSPICIOUS, 0)
    assertThat(listener.started).isEqualTo(1)
  }

  @Test
  fun `background ends the open hang and cancels tick`() {
    startInForeground()
    openHangInTick(300)
    advance(500)

    appStateProvider.background()
    executor.runPending()
    sdkExecutor.runAll()

    assertThat(records.single().durationMs).isEqualTo(800)
    assertThat(signal.isStarted).isFalse()
    assertThat(executor.activePeriodicTasks).isEmpty()
  }

  @Test
  fun `foreground after background starts the signals and the tick again`() {
    startInForeground()
    appStateProvider.background()
    executor.runPending()

    appStateProvider.foreground()
    executor.runPending()

    assertThat(signal.startCount).isEqualTo(2)
    assertThat(signal.isStarted).isTrue()
    assertThat(executor.activePeriodicTasks).hasSize(1)
  }

  @Test
  fun `signal cancel cancels the recording and sends no record`() {
    startInForeground()
    openHangInTick(300)
    collector.calls.clear()

    signal.cancel()
    executor.runTick()
    sdkExecutor.runAll()

    assertThat(collector.calls).isEmpty()
    assertThat(records).isEmpty()
  }

  @Test
  fun `signal change from another thread runs on the manager thread`() {
    startInForeground()

    val thread = Thread { signal.report(HangSignal.State.SUSPICIOUS, 0) }
    thread.start()
    thread.join()
    assertThat(listener.started).isEqualTo(0)

    executor.runPending()
    assertThat(listener.started).isEqualTo(1)
  }

  @Test
  fun `signal duration from another thread is measured at the call`() {
    startInForeground()

    val thread = Thread { signal.report(HangSignal.State.SUSPICIOUS, 300) }
    thread.start()
    thread.join()
    advance(100)
    executor.runPending()
    signal.report(HangSignal.State.IDLE, 0)
    sdkExecutor.runAll()

    assertThat(records.single().durationMs).isEqualTo(400)
  }

  @Test
  fun `no subscription starts no thread`() {
    appStateProvider.foreground()

    assertThat(executors).isEmpty()
    assertThat(appStateProvider.listeners).isEmpty()
    assertThat(signal.startCount).isEqualTo(0)
  }

  @Test
  fun `signals start only at foreground`() {
    manager.subscribe(listener)
    executor.runPending()

    assertThat(executors).hasSize(1)
    assertThat(signal.startCount).isEqualTo(0)
    assertThat(executor.activePeriodicTasks).isEmpty()
  }

  @Test
  fun `subscribe and unsubscribe from one thread keep their order`() {
    appStateProvider.foreground()

    manager.subscribe(listener)
    manager.unsubscribe(listener)
    executor.runPending()

    assertThat(signal.isStarted).isFalse()
    assertThat(appStateProvider.listeners).isEmpty()
  }

  @Test
  fun `last unsubscribe stops signals and the tick but keeps the executor`() {
    val otherListener = FakeListener()
    manager.subscribe(otherListener)
    startInForeground()

    manager.unsubscribe(otherListener)
    executor.runPending()
    assertThat(signal.isStarted).isTrue()

    manager.unsubscribe(listener)
    executor.runPending()
    assertThat(signal.isStarted).isFalse()
    assertThat(executor.activePeriodicTasks).isEmpty()
    assertThat(executor.isShutdown).isFalse()
    assertThat(appStateProvider.listeners).isEmpty()
  }

  @Test
  fun `subscribers share one app state listener`() {
    manager.subscribe(listener)
    manager.subscribe(FakeListener())
    executor.runPending()

    assertThat(appStateProvider.listeners).hasSize(1)
  }

  @Test
  fun `last unsubscribe drops the open hang without a record`() {
    startInForeground()
    openHangInTick(300)

    manager.unsubscribe(listener)
    executor.runPending()
    sdkExecutor.runAll()

    assertThat(records).isEmpty()
  }

  @Test
  fun `last unsubscribe does not close signals or stores`() {
    startInForeground()

    manager.unsubscribe(listener)
    executor.runPending()

    assertThat(signal.closeCount).isEqualTo(0)
    assertThat(store.calls).doesNotContain("close")
  }

  @Test
  fun `hang after the last unsubscribe and a new subscribe starts with an empty record`() {
    startInForeground()
    openHangInTick(300)
    manager.unsubscribe(listener)
    executor.runPending()

    startInForeground()
    openHangInTick(300)
    signal.report(HangSignal.State.IDLE, 0)

    assertThat(recordedValues().single()).containsExactly("s2")
  }

  @Test
  fun `subscribe after last unsubscribe reuses the executor`() {
    startInForeground()
    manager.unsubscribe(listener)
    executor.runPending()

    startInForeground()

    assertThat(executors).hasSize(1)
    assertThat(signal.isStarted).isTrue()
    assertThat(executor.activePeriodicTasks).hasSize(1)
  }

  @Test
  fun `signal that throws on stop leaves no second tick loop`() {
    signal.throwOnStop = true
    startInForeground()
    openHangInTick(300)

    appStateProvider.background()
    executor.runPending()
    sdkExecutor.runAll()
    appStateProvider.foreground()
    executor.runPending()

    assertThat(executor.taskFailures).hasSize(1)
    assertThat(executor.activePeriodicTasks).hasSize(1)
    assertThat(records).hasSize(1)
  }

  @Test
  fun `exception in collector does not stop the tick loop`() {
    startInForeground()
    collector.throwOnSample = true
    openHangInTick(300)
    collector.throwOnSample = false

    executor.runTick()

    assertThat(collector.calls).hasSize(2)
    verify(logger).log(eq(SentryLevel.ERROR), any<String>(), any<Throwable>())
  }

  @Test
  fun `close closes signals and stores once on the manager thread`() {
    startInForeground()

    manager.close()
    assertThat(signal.closeCount).isEqualTo(0)
    assertThat(store.calls).doesNotContain("close")
    executor.runPending()
    manager.close()
    executor.runPending()

    assertThat(signal.closeCount).isEqualTo(1)
    assertThat(store.calls).containsExactly("close")
    assertThat(executor.isShutdown).isTrue()
  }

  @Test
  fun `store that fails to close is logged and the next stores are closed`() {
    val secondStore = FakeStore()
    store.throwOnClose = true
    createRecorder(
      collectors = listOf(setup(collector, store), setup(FakeCollector("t"), secondStore)),
    )

    manager.close()

    assertThat(secondStore.calls).containsExactly("close")
    verify(logger)
      .log(eq(SentryLevel.WARNING), eq("Failed to close a hang sample store"), any<Throwable>())
  }

  @Test
  fun `signal that throws on close does not skip closing the stores`() {
    signal.throwOnClose = true
    startInForeground()

    manager.close()
    executor.runPending()

    assertThat(store.calls).containsExactly("close")
    assertThat(executor.taskFailures).hasSize(1)
    assertThat(executor.isShutdown).isTrue()
  }

  @Test
  fun `close after the last unsubscribe still closes everything`() {
    startInForeground()
    manager.unsubscribe(listener)
    executor.runPending()

    manager.close()
    executor.runPending()

    assertThat(signal.closeCount).isEqualTo(1)
    assertThat(store.calls).containsExactly("close")
    assertThat(executor.isShutdown).isTrue()
  }

  @Test
  fun `close stops ticks and ignores later calls`() {
    var ticks = 0
    manager.addTickListener { ticks++ }
    startInForeground()

    manager.close()
    executor.runPending()
    executor.runTick()
    assertThat(ticks).isEqualTo(0)

    manager.close()
    manager.subscribe(listener)
    manager.unsubscribe(listener)
    appStateProvider.foreground()
    signal.report(HangSignal.State.SUSPICIOUS, 0)
    executor.runTick()

    assertThat(ticks).isEqualTo(0)
    assertThat(executors).hasSize(1)
    assertThat(executor.taskFailures).isEmpty()
    assertThat(signal.isStarted).isFalse()
    assertThat(store.calls).containsExactly("close")
  }

  @Test
  fun `close removes the app state listener`() {
    startInForeground()

    manager.close()
    executor.runPending()

    assertThat(appStateProvider.listeners).isEmpty()
  }

  @Test
  fun `close drops the open hang without a record`() {
    startInForeground()
    openHangInTick(300)

    manager.close()
    executor.runPending()
    sdkExecutor.runAll()

    assertThat(records).isEmpty()
  }

  @Test
  fun `record dropped by a full sdk executor queue is logged`() {
    options.executorService = NonOverridableNoOpSentryExecutorService()
    startInForeground()
    openHangInTick(300)

    signal.report(HangSignal.State.IDLE, 0)
    executor.runPending()

    verify(logger)
      .log(eq(SentryLevel.WARNING), eq("Dropped a hang record, the SDK executor queue is full"))
  }

  @Test
  fun `close without subscription starts no thread`() {
    manager.close()
    manager.subscribe(listener)

    assertThat(executors).isEmpty()
  }

  @Test
  fun `close without subscription closes signals and stores`() {
    manager.close()

    assertThat(signal.closeCount).isEqualTo(1)
    assertThat(store.calls).containsExactly("close")
  }

  @Test
  fun `manager on the default executor records the stack of the watched thread`() {
    val parked = Thread { while (!Thread.currentThread().isInterrupted) LockSupport.park() }
    parked.isDaemon = true
    parked.start()
    var posts = 0
    val poster =
      object : ThreadPoster {
        // The probe runs only at the 10th tick, so the thread looks blocked for about 600 ms.
        override fun post(runnable: Runnable) {
          if (++posts == 10) {
            runnable.run()
          }
        }

        override fun remove(runnable: Runnable) = Unit
      }
    val ticker = JavaMonotonicTicker.getInstance()
    val recorded = CountDownLatch(1)
    var record: HangRecord? = null
    appStateProvider.foreground()
    val created =
      HangManager(
        SentryOptions().apply { executorService = ImmediateExecutorService() },
        appStateProvider,
        ticker,
        epochClock,
        DEFAULT_POLLING_INTERVAL_MS,
        DEFAULT_MAX_RECORDING_TIME_MS,
        MAX_SAMPLES,
        HangManager.DEFAULT_EXECUTOR_FACTORY,
        { metronome ->
          listOf(
            ThreadHangSignal(
              metronome,
              ticker,
              { false },
              poster,
              HangManager.DEFAULT_SUSPICIOUS_THRESHOLD_MS,
            ),
          )
        },
        listOf(
          HangCollectorSetup(
            ThreadSampleCollector(epochClock, parked),
            { stackTrace, builder -> builder.addStackTrace(stackTrace) },
            null,
          ),
        ),
      )

    created.subscribe(
      object : HangRecordingListener {
        override fun onHangStarted() = HangRecordingRequest(true, false)

        override fun onHangRecorded(hangRecord: HangRecord) {
          record = hangRecord
          recorded.countDown()
        }
      },
    )

    try {
      assertThat(recorded.await(10, TimeUnit.SECONDS)).isTrue()
    } finally {
      created.close()
      parked.interrupt()
      parked.join()
    }
    assertThat(record!!.stackTraces).isNotEmpty()
    assertThat(record!!.stackTraces.first().stack.map { it.methodName }).contains("park")
  }

  private class FakeAppStateProvider : AppStateProvider {
    val listeners = mutableListOf<AppState.AppStateListener>()
    private var inBackground: Boolean? = null

    override fun addAppStateListener(listener: AppState.AppStateListener) {
      listeners.add(listener)
      when (inBackground) {
        false -> listener.onForeground()
        true -> listener.onBackground()
        null -> Unit
      }
    }

    override fun removeAppStateListener(listener: AppState.AppStateListener) {
      listeners.remove(listener)
    }

    fun foreground() {
      inBackground = false
      listeners.toList().forEach { it.onForeground() }
    }

    fun background() {
      inBackground = true
      listeners.toList().forEach { it.onBackground() }
    }
  }

  private class FakeSignal(private val metronome: Metronome) : HangSignal {
    private var listener: HangSignalListener? = null
    private var nextTickAction: (() -> Unit)? = null
    private val tickListener = Metronome.TickListener { _ ->
      val action = nextTickAction
      nextTickAction = null
      action?.invoke()
    }
    var startCount = 0
    var closeCount = 0
    var throwOnStop = false
    var throwOnClose = false

    val isStarted: Boolean
      get() = listener != null

    override fun start(listener: HangSignalListener) {
      startCount++
      this.listener = listener
      metronome.addTickListener(tickListener)
    }

    override fun stop() {
      listener = null
      metronome.removeTickListener(tickListener)
      if (throwOnStop) {
        throw IllegalStateException("stop failed")
      }
    }

    override fun close() {
      closeCount++
      if (throwOnClose) {
        throw IllegalStateException("close failed")
      }
    }

    fun onNextTick(action: () -> Unit) {
      nextTickAction = action
    }

    fun report(state: HangSignal.State, durationMs: Long) {
      listener?.onSignalChanged(this, state, durationMs)
    }

    fun cancel() {
      listener?.onSignalCanceled(this)
    }
  }

  private class FakeListener(
    val records: MutableList<HangRecord> = mutableListOf(),
    var request: HangRecordingRequest? = HangRecordingRequest(true, false),
  ) : HangRecordingListener {
    var started = 0

    override fun onHangStarted(): HangRecordingRequest? {
      started++
      return request
    }

    override fun onHangRecorded(record: HangRecord) {
      records.add(record)
    }
  }

  /**
   * Returns the values "s1", "s2", ..., counting every sample call, also those that return null.
   */
  private class FakeCollector(private val prefix: String = "s") : HangDataCollector<String> {
    val calls = mutableListOf<String>()
    private var sampleCount = 0
    var returnNull = false
    var throwOnSample = false

    override fun sample(): String? {
      calls.add("sample")
      sampleCount++
      if (throwOnSample) {
        throw IllegalStateException("collector failed")
      }
      return if (returnNull) null else "$prefix$sampleCount"
    }
  }

  private class FakeStore : HangSampleStore<String> {
    val calls = mutableListOf<String>()
    var throwOnClear = false
    var throwOnAdd = false
    var throwOnClose = false

    override fun clear() {
      calls.add("clear")
      if (throwOnClear) {
        throw IOException("clear failed")
      }
    }

    override fun add(sample: String) {
      calls.add("add $sample")
      if (throwOnAdd) {
        throw IOException("add failed")
      }
    }

    override fun close() {
      calls.add("close")
      if (throwOnClose) {
        throw IOException("close failed")
      }
    }
  }

  private companion object {
    const val MAX_SAMPLES = 120

    /**
     * Puts each value into the record as the method of a stack frame, so a test can read it back.
     */
    val writer =
      HangRecordWriter<String> { value, record ->
        record.addStackTrace(AnrStackTrace(0, arrayOf(StackTraceElement("Fake", value, null, 0))))
      }
  }
}
