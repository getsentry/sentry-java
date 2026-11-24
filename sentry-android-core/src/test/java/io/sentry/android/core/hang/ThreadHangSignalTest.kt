package io.sentry.android.core.hang

import com.google.common.truth.Truth.assertThat
import io.sentry.IDebugManager
import io.sentry.time.TestMonotonicTicker
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import kotlin.test.BeforeTest
import kotlin.test.Test

class ThreadHangSignalTest {

  private class FakeMetronome : Metronome {
    val listeners = mutableListOf<Metronome.TickListener>()

    override fun addTickListener(listener: Metronome.TickListener) {
      listeners.add(listener)
    }

    override fun removeTickListener(listener: Metronome.TickListener) {
      listeners.remove(listener)
    }

    fun tick(nowMs: Long) = listeners.toList().forEach { it.onMetronomeTick(nowMs) }
  }

  private class FakePoster : ThreadPoster {
    val calls = mutableListOf<String>()
    var pending: Runnable? = null

    override fun post(runnable: Runnable) {
      calls.add("post")
      pending = runnable
    }

    override fun remove(runnable: Runnable) {
      calls.add("remove")
      if (pending === runnable) {
        pending = null
      }
    }

    fun runProbe() {
      pending?.run()
      pending = null
    }
  }

  private inner class RecordingListener : HangSignalListener {
    val changes = mutableListOf<Pair<HangSignal.State, Long>>()
    var canceled = 0

    override fun onSignalChanged(signal: HangSignal, state: HangSignal.State, durationMs: Long) {
      changes.add(state to nowMs - durationMs)
    }

    override fun onSignalCanceled(signal: HangSignal) {
      canceled++
    }
  }

  private lateinit var ticker: TestMonotonicTicker
  private lateinit var metronome: FakeMetronome
  private lateinit var poster: FakePoster
  private lateinit var listener: RecordingListener
  private var debuggerConnected = false
  private lateinit var signal: ThreadHangSignal

  private val nowMs: Long
    get() = NANOSECONDS.toMillis(ticker.tickNanos())

  @BeforeTest
  fun setUp() {
    ticker = TestMonotonicTicker(HOURS.toNanos(30))
    metronome = FakeMetronome()
    poster = FakePoster()
    listener = RecordingListener()
    signal = createSignal(HangManager.DEFAULT_SUSPICIOUS_THRESHOLD_MS)
    signal.start(listener)
  }

  private fun createSignal(suspiciousThresholdMs: Long) =
    ThreadHangSignal(
      metronome,
      ticker,
      IDebugManager { debuggerConnected },
      poster,
      suspiciousThresholdMs,
    )

  private fun advance(ms: Long) {
    ticker.advance(ms, MILLISECONDS)
  }

  private fun advanceAndTick(ms: Long) {
    advance(ms)
    metronome.tick(nowMs)
  }

  @Test
  fun `delay above 300ms sends suspicious with the duration since the last probe run`() {
    val start = nowMs
    advanceAndTick(66)
    poster.runProbe()
    val probeRun = nowMs
    advanceAndTick(HangManager.DEFAULT_SUSPICIOUS_THRESHOLD_MS + 1)

    assertThat(listener.changes).containsExactly(HangSignal.State.SUSPICIOUS to probeRun)
    assertThat(probeRun).isEqualTo(start + 66)
  }

  @Test
  fun `suspicious threshold comes from the constructor`() {
    signal.stop()
    signal = createSignal(1_000)
    signal.start(listener)

    advanceAndTick(1_000)
    assertThat(listener.changes).isEmpty()

    advanceAndTick(1)
    assertThat(listener.changes.map { it.first }).containsExactly(HangSignal.State.SUSPICIOUS)
  }

  @Test
  fun `probe run after suspicious sends idle with the duration since the probe run`() {
    advanceAndTick(400)
    advance(500)
    poster.runProbe()
    val probeRun = nowMs
    advanceAndTick(66)

    assertThat(listener.changes.map { it.first })
      .containsExactly(HangSignal.State.SUSPICIOUS, HangSignal.State.IDLE)
      .inOrder()
    assertThat(listener.changes.last().second).isEqualTo(probeRun)
  }

  @Test
  fun `suspicious is not sent again until idle`() {
    advanceAndTick(400)
    advanceAndTick(66)
    advanceAndTick(66)

    assertThat(listener.changes).hasSize(1)
  }

  @Test
  fun `delay below 300ms sends nothing`() {
    advanceAndTick(66)
    poster.runProbe()
    advanceAndTick(HangManager.DEFAULT_SUSPICIOUS_THRESHOLD_MS)

    assertThat(listener.changes).isEmpty()
    assertThat(listener.canceled).isEqualTo(0)
  }

  @Test
  fun `start resets probe time after a long stop`() {
    signal.stop()
    ticker.advance(10_000, MILLISECONDS)
    signal.start(listener)
    advanceAndTick(66)

    assertThat(listener.changes).isEmpty()
  }

  @Test
  fun `stop removes the tick listener`() {
    signal.stop()

    assertThat(metronome.listeners).isEmpty()
  }

  @Test
  fun `close removes the tick listener and the pending probe`() {
    advanceAndTick(66)

    signal.close()

    assertThat(metronome.listeners).isEmpty()
    assertThat(poster.pending).isNull()
  }

  @Test
  fun `debugger connected while suspicious cancels`() {
    advanceAndTick(400)
    debuggerConnected = true
    advanceAndTick(66)

    assertThat(listener.changes.map { it.first }).containsExactly(HangSignal.State.SUSPICIOUS)
    assertThat(listener.canceled).isEqualTo(1)
  }

  @Test
  fun `debugger connected sends no state`() {
    debuggerConnected = true
    advanceAndTick(5_000)
    advanceAndTick(5_000)

    assertThat(listener.changes).isEmpty()
    assertThat(listener.canceled).isEqualTo(0)
  }

  @Test
  fun `time at a breakpoint does not count after the debugger detaches`() {
    debuggerConnected = true
    advanceAndTick(5_000)
    debuggerConnected = false
    advanceAndTick(66)

    assertThat(listener.changes).isEmpty()
  }

  @Test
  fun `each tick removes the pending probe before posting`() {
    advanceAndTick(66)
    advanceAndTick(66)

    assertThat(poster.calls).containsExactly("remove", "post", "remove", "post").inOrder()
  }
}
