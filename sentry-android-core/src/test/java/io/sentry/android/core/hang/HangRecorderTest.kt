package io.sentry.android.core.hang

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel
import io.sentry.android.core.anr.AnrStackTrace
import java.io.IOException
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class HangRecorderTest {

  private val logger = mock<ILogger>()
  private val collector = FakeCollector()
  private val store = FakeStore()
  private val writer =
    HangRecordWriter<String> { value, record ->
      record.addStackTrace(AnrStackTrace(0, arrayOf(StackTraceElement("Fake", value, null, 0))))
    }

  private fun start(
    vararg listeners: FakeListener,
    collectors: List<HangCollectorSetup<*>> = listOf(HangCollectorSetup(collector, writer, store)),
  ): HangRecorder = HangRecorder.start(listeners.toList(), collectors, 100, 5_000, logger)

  private fun HangRecord.values() = stackTraces.map { it.stack.single().methodName }

  @Test
  fun `a hang without a listener that asks for a record samples nothing`() {
    val recorder = start(FakeListener(null), FakeListener(HangRecordingRequest(false, true)))

    recorder.sample(120)

    assertThat(recorder.isRecording).isFalse()
    assertThat(recorder.listeners).isEmpty()
    assertThat(collector.sampleCount).isEqualTo(0)
    assertThat(store.calls).isEmpty()
  }

  @Test
  fun `asks every listener`() {
    val first = FakeListener(HangRecordingRequest(true, false))
    val second = FakeListener(null)

    start(first, second)

    assertThat(first.started).isEqualTo(1)
    assertThat(second.started).isEqualTo(1)
  }

  @Test
  fun `persist request clears the store and writes each sample`() {
    val recorder =
      start(
        FakeListener(HangRecordingRequest(true, false)),
        FakeListener(HangRecordingRequest(true, true)),
      )

    recorder.sample(120)
    recorder.sample(120)

    assertThat(store.calls).containsExactly("clear", "add s1", "add s2").inOrder()
  }

  @Test
  fun `record without persist request does not touch the store`() {
    val recorder = start(FakeListener(HangRecordingRequest(true, false)))

    recorder.sample(120)

    assertThat(store.calls).isEmpty()
    assertThat(recorder.end(200).values()).containsExactly("s1")
  }

  @Test
  fun `stops sampling after maxSamples ticks`() {
    val recorder = start(FakeListener(HangRecordingRequest(true, false)))

    repeat(5) { recorder.sample(3) }

    assertThat(collector.sampleCount).isEqualTo(3)
    assertThat(recorder.end(200).values()).containsExactly("s1", "s2", "s3").inOrder()
  }

  @Test
  fun `a tick without a value still counts against maxSamples`() {
    val recorder = start(FakeListener(HangRecordingRequest(true, false)))
    collector.returnNull = true

    recorder.sample(2)
    recorder.sample(2)
    collector.returnNull = false
    recorder.sample(2)

    assertThat(collector.sampleCount).isEqualTo(2)
    assertThat(recorder.end(200).values()).isEmpty()
  }

  @Test
  fun `store write failure stops persisting but not sampling`() {
    val other = FakeCollector("o")
    val recorder =
      start(
        FakeListener(HangRecordingRequest(true, true)),
        collectors =
          listOf(
            HangCollectorSetup(collector, writer, store),
            HangCollectorSetup(other, writer, null),
          ),
      )
    store.throwOnAdd = true

    recorder.sample(120)
    store.throwOnAdd = false
    recorder.sample(120)

    assertThat(store.calls).containsExactly("clear", "add s1").inOrder()
    assertThat(recorder.end(200).values()).containsExactly("s1", "o1", "s2", "o2").inOrder()
    verify(logger, times(1)).log(eq(SentryLevel.WARNING), any<String>(), any<IOException>())
  }

  @Test
  fun `store clear failure stops persisting`() {
    store.throwOnClear = true
    val recorder = start(FakeListener(HangRecordingRequest(true, true)))

    recorder.sample(120)

    assertThat(store.calls).containsExactly("clear")
    verify(logger).log(eq(SentryLevel.WARNING), any<String>(), any<IOException>())
  }

  @Test
  fun `listeners are the ones that asked for a record`() {
    val recording1 = FakeListener(HangRecordingRequest(true, false))
    val recording2 = FakeListener(HangRecordingRequest(true, true))
    val notRecording = FakeListener(null)
    val recorder = start(recording1, recording2, notRecording)

    assertThat(recorder.listeners).containsExactly(recording1, recording2).inOrder()
  }

  @Test
  fun `end builds the record with duration and start timestamp`() {
    val recorder = start(FakeListener(HangRecordingRequest(true, false)))

    val record = recorder.end(8_100)

    assertThat(recorder.startTimeMs).isEqualTo(100)
    assertThat(record.durationMs).isEqualTo(8_000)
    assertThat(record.startTimestampMs).isEqualTo(5_000)
  }

  private class FakeListener(private val request: HangRecordingRequest?) : HangRecordingListener {
    var started = 0

    override fun onHangStarted(): HangRecordingRequest? {
      started++
      return request
    }

    override fun onHangRecorded(record: HangRecord) = Unit
  }

  private class FakeCollector(private val prefix: String = "s") : HangDataCollector<String> {
    var sampleCount = 0
    var returnNull = false

    override fun sample(): String? {
      sampleCount++
      return if (returnNull) null else "$prefix$sampleCount"
    }
  }

  private class FakeStore : HangSampleStore<String> {
    val calls = mutableListOf<String>()
    var throwOnClear = false
    var throwOnAdd = false

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

    override fun close() = Unit
  }
}
