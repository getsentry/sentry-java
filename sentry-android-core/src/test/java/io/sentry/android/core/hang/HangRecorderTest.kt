package io.sentry.android.core.hang

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.AppState
import io.sentry.android.core.AppStateProvider
import io.sentry.android.core.anr.AnrStackTrace
import io.sentry.android.core.hang.HangRecorder.DEFAULT_MAX_RECORDING_TIME_MS
import io.sentry.android.core.hang.HangRecorder.DEFAULT_POLLING_INTERVAL_MS
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import io.sentry.test.NonOverridableNoOpSentryExecutorService
import io.sentry.time.EpochClock
import io.sentry.time.JavaMonotonicTicker
import io.sentry.time.TestMonotonicTicker
import io.sentry.time.Timestamp
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
import org.mockito.kotlin.verify

class HangRecorderTest {

  private lateinit var ticker: TestMonotonicTicker
  private var unixTimeMs = 1_700_000_000_000L
  private lateinit var logger: ILogger
  private lateinit var sdkExecutor: DeferredExecutorService
  private lateinit var options: SentryOptions
  private val epochClock = EpochClock { Timestamp.ofEpochNanos(MILLISECONDS.toNanos(unixTimeMs)) }
  private lateinit var appStateProvider: FakeAppStateProvider
  private val executors = mutableListOf<FakeScheduledExecutorService>()
  private lateinit var recorder: HangRecorder
  private lateinit var signal: FakeSignal
  private val signals = mutableListOf<FakeSignal>()
  private lateinit var collector: FakeCollector
  private val records = mutableListOf<HangRecord>()
  private val listener = HangRecorderListener { records.add(it) }

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
    createRecorder()
  }

  private fun createRecorder(
    signalCount: Int = 1,
    collectors: List<HangDataCollector> = listOf(collector),
  ) {
    recorder =
      HangRecorder(
        options,
        appStateProvider,
        ticker,
        epochClock,
        DEFAULT_POLLING_INTERVAL_MS,
        DEFAULT_MAX_RECORDING_TIME_MS,
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

  private fun nowMs(): Long = TimeUnit.NANOSECONDS.toMillis(ticker.tickNanos())

  private fun advance(ms: Long) {
    ticker.advance(ms, MILLISECONDS)
    unixTimeMs += ms
  }

  private fun startInForeground() {
    recorder.subscribe(listener)
    executor.runPending()
    appStateProvider.foreground()
    executor.runPending()
  }

  private fun openIntervalInTick(durationMs: Long) {
    signal.onNextTick { signal.report(HangSignal.State.SUSPICIOUS, durationMs) }
    executor.runTick()
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
  fun `suspicious opens interval and starts collectors`() {
    startInForeground()
    val sinceMs = nowMs() - 300

    openIntervalInTick(300)

    assertThat(collector.calls).containsExactly("start $sinceMs", "sample $sinceMs").inOrder()
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
    recorder.addTickListener { calls.add("tick") }
    startInForeground()

    val sinceMs = nowMs() - 300
    openIntervalInTick(300)
    executor.runTick()

    assertThat(calls)
      .containsExactly("tick", "start $sinceMs", "sample $sinceMs", "tick", "sample $sinceMs")
      .inOrder()
  }

  @Test
  fun `idle closes interval and notifies listeners on sdk executor`() {
    startInForeground()
    val suspiciousSinceMs = nowMs() - 300
    val timestampAtOpenMs = unixTimeMs
    openIntervalInTick(300)
    val samples = listOf(AnrStackTrace(unixTimeMs, arrayOf()))
    collector.onRecordingStoppedAction = { it.setSamples(samples) }
    advance(1_000)
    val idleSinceMs = nowMs() - 20

    signal.report(HangSignal.State.IDLE, 20)

    assertThat(collector.calls.last()).isEqualTo("stop $suspiciousSinceMs")
    assertThat(records).isEmpty()
    sdkExecutor.runAll()
    val record = records.single()
    assertThat(record.durationMs).isEqualTo(idleSinceMs - suspiciousSinceMs)
    assertThat(record.startTimestampMs).isEqualTo(timestampAtOpenMs - 300)
    assertThat(record.samples).isSameInstanceAs(samples)
  }

  @Test
  fun `record carries the runtime stats of a runtime stats collector`() {
    var gcCount = 10L
    val source = RuntimeStatsSource {
      RuntimeStatsSnapshot().also {
        it.gcCount = gcCount
        it.totalMemory = 50
      }
    }
    createRecorder(collectors = listOf(RuntimeStatsCollector(source)))
    startInForeground()
    openIntervalInTick(300)
    gcCount = 13

    signal.report(HangSignal.State.IDLE, 0)
    sdkExecutor.runAll()

    assertThat(records.single().runtimeStats)
      .containsAtLeast(
        RuntimeStatsCollector.KEY_TOTAL_MEMORY,
        50L,
        RuntimeStatsCollector.KEY_GC_COUNT_DELTA,
        3L,
      )
  }

  @Test
  fun `no sample after idle`() {
    startInForeground()
    openIntervalInTick(300)
    signal.report(HangSignal.State.IDLE, 0)
    collector.calls.clear()

    executor.runTick()

    assertThat(collector.calls).isEmpty()
  }

  @Test
  fun `idle without open interval is ignored`() {
    startInForeground()

    signal.report(HangSignal.State.IDLE, 0)
    executor.runTick()
    sdkExecutor.runAll()

    assertThat(collector.calls).isEmpty()
    assertThat(records).isEmpty()
  }

  @Test
  fun `second suspicious from another signal does not reopen interval`() {
    createRecorder(signalCount = 2)
    val otherSignal = signals[1]
    startInForeground()
    openIntervalInTick(300)
    advance(100)

    otherSignal.report(HangSignal.State.SUSPICIOUS, 0)
    advance(100)
    signal.report(HangSignal.State.IDLE, 0)
    sdkExecutor.runAll()

    assertThat(collector.calls.count { it.startsWith("start") }).isEqualTo(1)
    assertThat(records.single().durationMs).isEqualTo(500)
  }

  @Test
  fun `signal factory gets the recorder as metronome`() {
    var metronome: Metronome? = null
    val created =
      HangRecorder(
        options,
        appStateProvider,
        ticker,
        epochClock,
        DEFAULT_POLLING_INTERVAL_MS,
        DEFAULT_MAX_RECORDING_TIME_MS,
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
  fun `default executor factory runs the recorder on a daemon thread`() {
    val executor = HangRecorder.DEFAULT_EXECUTOR_FACTORY.create()
    try {
      val thread = executor.submit<Thread> { Thread.currentThread() }.get(10, TimeUnit.SECONDS)

      assertThat(thread.name).isEqualTo("SentryHangRecorder")
      assertThat(thread.isDaemon).isTrue()
    } finally {
      executor.shutdown()
    }
  }

  @Test
  fun `maxRecordingTime ends interval with durationMs 8000 and later idle is ignored`() {
    startInForeground()
    openIntervalInTick(0)
    tickUntilRecorded()
    val samplesAtEnd = collector.calls.count { it.startsWith("sample") }

    executor.runTick()
    signal.report(HangSignal.State.IDLE, 0)
    executor.runTick()
    sdkExecutor.runAll()

    assertThat(records.single().durationMs).isEqualTo(DEFAULT_MAX_RECORDING_TIME_MS)
    assertThat(collector.calls.count { it.startsWith("sample") }).isEqualTo(samplesAtEnd)
  }

  @Test
  fun `maxRecordingTime end marks the record as ended at maxRecordingTime`() {
    startInForeground()
    openIntervalInTick(0)

    tickUntilRecorded()

    assertThat(records.single().hasEndedAtMaxRecordingTime()).isTrue()
  }

  @Test
  fun `idle end does not mark the record as ended at maxRecordingTime`() {
    startInForeground()
    openIntervalInTick(300)
    advance(500)

    signal.report(HangSignal.State.IDLE, 0)
    sdkExecutor.runAll()

    assertThat(records.single().hasEndedAtMaxRecordingTime()).isFalse()
  }

  @Test
  fun `background end does not mark the record as ended at maxRecordingTime`() {
    startInForeground()
    openIntervalInTick(300)
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
    openIntervalInTick(0)

    tickUntilRecorded()

    assertThat(records.single().durationMs).isEqualTo(DEFAULT_MAX_RECORDING_TIME_MS)
    assertThat(records.single().hasEndedAtMaxRecordingTime()).isTrue()
  }

  @Test
  fun `after maxRecordingTime no interval opens until idle`() {
    startInForeground()
    openIntervalInTick(0)
    advance(DEFAULT_MAX_RECORDING_TIME_MS)
    executor.runTick()
    collector.calls.clear()

    signal.report(HangSignal.State.SUSPICIOUS, 0)
    assertThat(collector.calls).isEmpty()

    signal.report(HangSignal.State.IDLE, 0)
    signal.report(HangSignal.State.SUSPICIOUS, 0)
    assertThat(collector.calls).containsExactly("start ${nowMs()}")
  }

  @Test
  fun `background ends open interval and cancels tick`() {
    startInForeground()
    openIntervalInTick(300)
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
    openIntervalInTick(300)
    collector.calls.clear()

    signal.cancel()
    executor.runTick()
    sdkExecutor.runAll()

    assertThat(collector.calls).containsExactly("cancel")
    assertThat(records).isEmpty()
  }

  @Test
  fun `signal change from another thread runs on the recorder thread`() {
    startInForeground()
    val sinceMs = nowMs()

    val thread = Thread { signal.report(HangSignal.State.SUSPICIOUS, 0) }
    thread.start()
    thread.join()
    assertThat(collector.calls).isEmpty()

    executor.runPending()
    assertThat(collector.calls).containsExactly("start $sinceMs")
  }

  @Test
  fun `signal duration from another thread is measured at the call`() {
    startInForeground()
    val sinceMs = nowMs() - 300

    val thread = Thread { signal.report(HangSignal.State.SUSPICIOUS, 300) }
    thread.start()
    thread.join()
    advance(100)
    executor.runPending()

    assertThat(collector.calls).containsExactly("start $sinceMs")
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
    recorder.subscribe(listener)
    executor.runPending()

    assertThat(executors).hasSize(1)
    assertThat(signal.startCount).isEqualTo(0)
    assertThat(executor.activePeriodicTasks).isEmpty()
  }

  @Test
  fun `subscribe and unsubscribe from one thread keep their order`() {
    appStateProvider.foreground()

    recorder.subscribe(listener)
    recorder.unsubscribe(listener)
    executor.runPending()

    assertThat(signal.isStarted).isFalse()
    assertThat(appStateProvider.listeners).isEmpty()
  }

  @Test
  fun `last unsubscribe stops signals and the tick but keeps the executor`() {
    val otherListener = HangRecorderListener {}
    recorder.subscribe(otherListener)
    startInForeground()

    recorder.unsubscribe(otherListener)
    executor.runPending()
    assertThat(signal.isStarted).isTrue()

    recorder.unsubscribe(listener)
    executor.runPending()
    assertThat(signal.isStarted).isFalse()
    assertThat(executor.activePeriodicTasks).isEmpty()
    assertThat(executor.isShutdown).isFalse()
    assertThat(appStateProvider.listeners).isEmpty()
  }

  @Test
  fun `subscribers share one app state listener`() {
    recorder.subscribe(listener)
    recorder.subscribe(HangRecorderListener {})
    executor.runPending()

    assertThat(appStateProvider.listeners).hasSize(1)
  }

  @Test
  fun `last unsubscribe drops an open interval without a record`() {
    startInForeground()
    openIntervalInTick(300)

    recorder.unsubscribe(listener)
    executor.runPending()
    sdkExecutor.runAll()

    assertThat(collector.calls.last()).isEqualTo("cancel")
    assertThat(collector.calls.none { it.startsWith("stop") }).isTrue()
    assertThat(records).isEmpty()
  }

  @Test
  fun `last unsubscribe does not close signals or collectors`() {
    startInForeground()

    recorder.unsubscribe(listener)
    executor.runPending()

    assertThat(signal.closeCount).isEqualTo(0)
    assertThat(collector.calls).doesNotContain("close")
  }

  @Test
  fun `subscribe after last unsubscribe reuses the executor`() {
    startInForeground()
    recorder.unsubscribe(listener)
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
    openIntervalInTick(300)

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
  fun `collector that throws on cancel does not block a later subscribe`() {
    collector.throwOnCancel = true
    startInForeground()
    openIntervalInTick(300)
    recorder.unsubscribe(listener)
    executor.runPending()
    collector.throwOnCancel = false

    startInForeground()

    assertThat(executor.taskFailures).hasSize(1)
    assertThat(signal.isStarted).isTrue()
    assertThat(executor.activePeriodicTasks).hasSize(1)
  }

  @Test
  fun `exception in collector does not stop the tick loop`() {
    startInForeground()
    collector.throwOnSample = true
    openIntervalInTick(300)
    collector.throwOnSample = false

    executor.runTick()

    assertThat(collector.calls.count { it.startsWith("sample") }).isEqualTo(2)
    verify(logger).log(eq(SentryLevel.ERROR), any<String>(), any<Throwable>())
  }

  @Test
  fun `close closes signals and collectors once on the recorder thread`() {
    startInForeground()

    recorder.close()
    assertThat(signal.closeCount).isEqualTo(0)
    assertThat(collector.calls).doesNotContain("close")
    executor.runPending()
    recorder.close()
    executor.runPending()

    assertThat(signal.closeCount).isEqualTo(1)
    assertThat(collector.calls.count { it == "close" }).isEqualTo(1)
    assertThat(executor.isShutdown).isTrue()
  }

  @Test
  fun `close after the last unsubscribe still closes everything`() {
    startInForeground()
    recorder.unsubscribe(listener)
    executor.runPending()

    recorder.close()
    executor.runPending()

    assertThat(signal.closeCount).isEqualTo(1)
    assertThat(collector.calls.count { it == "close" }).isEqualTo(1)
    assertThat(executor.isShutdown).isTrue()
  }

  @Test
  fun `signal that throws on close still lets the executor shut down`() {
    signal.throwOnClose = true
    startInForeground()

    recorder.close()
    executor.runPending()

    assertThat(executor.taskFailures).hasSize(1)
    assertThat(executor.isShutdown).isTrue()
  }

  @Test
  fun `collector that throws on close still lets the executor shut down`() {
    collector.throwOnClose = true
    startInForeground()

    recorder.close()
    executor.runPending()

    assertThat(executor.taskFailures).hasSize(1)
    assertThat(executor.isShutdown).isTrue()
  }

  @Test
  fun `close stops ticks and ignores later calls`() {
    var ticks = 0
    recorder.addTickListener { ticks++ }
    startInForeground()

    recorder.close()
    executor.runPending()
    executor.runTick()
    assertThat(ticks).isEqualTo(0)

    recorder.close()
    recorder.subscribe(listener)
    recorder.unsubscribe(listener)
    appStateProvider.foreground()
    signal.report(HangSignal.State.SUSPICIOUS, 0)
    executor.runTick()

    assertThat(ticks).isEqualTo(0)
    assertThat(executors).hasSize(1)
    assertThat(executor.taskFailures).isEmpty()
    assertThat(signal.isStarted).isFalse()
    assertThat(collector.calls.count { it == "close" }).isEqualTo(1)
  }

  @Test
  fun `close removes the app state listener`() {
    startInForeground()

    recorder.close()
    executor.runPending()

    assertThat(appStateProvider.listeners).isEmpty()
  }

  @Test
  fun `close drops an open interval without a record`() {
    startInForeground()
    openIntervalInTick(300)

    recorder.close()
    executor.runPending()
    sdkExecutor.runAll()

    assertThat(records).isEmpty()
    assertThat(collector.calls).contains("cancel")
  }

  @Test
  fun `record dropped by a full sdk executor queue is logged`() {
    options.executorService = NonOverridableNoOpSentryExecutorService()
    startInForeground()
    openIntervalInTick(300)

    signal.report(HangSignal.State.IDLE, 0)
    executor.runPending()

    verify(logger)
      .log(eq(SentryLevel.WARNING), eq("Dropped a hang record, the SDK executor queue is full"))
  }

  @Test
  fun `close without subscription starts no thread`() {
    recorder.close()
    recorder.subscribe(listener)

    assertThat(executors).isEmpty()
  }

  @Test
  fun `close without subscription closes signals and collectors`() {
    recorder.close()

    assertThat(signal.closeCount).isEqualTo(1)
    assertThat(collector.calls).containsExactly("close")
  }

  @Test
  fun `recorder on the default executor records the stack of the watched thread`() {
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
      HangRecorder(
        SentryOptions().apply { executorService = ImmediateExecutorService() },
        appStateProvider,
        ticker,
        epochClock,
        DEFAULT_POLLING_INTERVAL_MS,
        DEFAULT_MAX_RECORDING_TIME_MS,
        HangRecorder.DEFAULT_EXECUTOR_FACTORY,
        { metronome ->
          listOf(
            ThreadHangSignal(
              metronome,
              ticker,
              { false },
              poster,
              HangRecorder.DEFAULT_SUSPICIOUS_THRESHOLD_MS,
            ),
          )
        },
        listOf(ThreadSampleCollector(SentryOptions(), epochClock, ticker, parked)),
      )

    created.subscribe {
      record = it
      recorded.countDown()
    }

    try {
      assertThat(recorded.await(10, TimeUnit.SECONDS)).isTrue()
    } finally {
      created.close()
      parked.interrupt()
      parked.join()
    }
    assertThat(record!!.samples).isNotEmpty()
    assertThat(record!!.samples.first().stack.map { it.methodName }).contains("park")
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

  private class FakeCollector : HangDataCollector {
    val calls = mutableListOf<String>()
    var throwOnSample = false
    var throwOnCancel = false
    var throwOnClose = false
    var onRecordingStoppedAction: (HangRecord.Builder) -> Unit = {}

    override fun onRecordingStarted(interval: HangInterval) {
      calls.add("start ${interval.startTimeMs}")
    }

    override fun onSample(interval: HangInterval) {
      calls.add("sample ${interval.startTimeMs}")
      if (throwOnSample) {
        throw IllegalStateException("collector failed")
      }
    }

    override fun onRecordingStopped(interval: HangInterval, out: HangRecord.Builder) {
      calls.add("stop ${interval.startTimeMs}")
      onRecordingStoppedAction(out)
    }

    override fun onRecordingCanceled() {
      calls.add("cancel")
      if (throwOnCancel) {
        throw IllegalStateException("cancel failed")
      }
    }

    override fun close() {
      calls.add("close")
      if (throwOnClose) {
        throw IllegalStateException("close failed")
      }
    }
  }
}
