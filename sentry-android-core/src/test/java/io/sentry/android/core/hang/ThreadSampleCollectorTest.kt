package io.sentry.android.core.hang

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryOptions
import io.sentry.android.core.anr.AnrProfileManager
import io.sentry.android.core.anr.AnrProfileRotationHelper
import io.sentry.android.core.anr.AnrStackTrace
import io.sentry.time.EpochClock
import io.sentry.time.TestMonotonicTicker
import io.sentry.time.Timestamp
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.locks.LockSupport
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ThreadSampleCollectorTest {

  private lateinit var cacheDir: File
  private lateinit var ticker: TestMonotonicTicker
  private var unixTimeMs = 1_700_000_000_000L
  private lateinit var epochClock: EpochClock
  private lateinit var options: SentryOptions
  private lateinit var collector: ThreadSampleCollector
  private lateinit var interval: HangInterval

  @BeforeTest
  fun setUp() {
    cacheDir = Files.createTempDirectory("thread-sample-collector").toFile()
    // Rotation moves an existing file once per process, do it before any file is seeded.
    AnrProfileRotationHelper.getFileForRecording(cacheDir)
    ticker = TestMonotonicTicker(HOURS.toNanos(30))
    epochClock = EpochClock { Timestamp.ofEpochNanos(MILLISECONDS.toNanos(unixTimeMs)) }
    options = SentryOptions()
    options.cacheDirPath = cacheDir.absolutePath
    collector = ThreadSampleCollector(options, epochClock, ticker, Thread.currentThread())
  }

  @AfterTest
  fun tearDown() {
    collector.close()
    cacheDir.deleteRecursively()
  }

  private fun nowMs(): Long = TimeUnit.NANOSECONDS.toMillis(ticker.tickNanos())

  private fun startInterval() {
    interval = HangInterval(nowMs())
    collector.onRecordingStarted(interval)
  }

  private fun advance(ms: Long) {
    ticker.advance(ms, MILLISECONDS)
    unixTimeMs += ms
  }

  private fun sampleEvery66Ms(count: Int) {
    repeat(count) {
      collector.onSample(interval)
      advance(66)
    }
  }

  private fun file() = File(cacheDir, "anr_profile")

  private fun readFile(): List<AnrStackTrace> {
    // The collector keeps the queue open, so release it before reading from a second manager.
    collector.close()
    AnrProfileManager(options, file()).use {
      return it.load().stacks
    }
  }

  private fun seedFile(timestampMs: Long) {
    AnrProfileManager(options, file()).use { it.add(AnrStackTrace(timestampMs, emptyArray())) }
  }

  @Test
  fun `samples stay in memory before 700ms`() {
    startInterval()
    sampleEvery66Ms(10) // 0..594 ms

    val builder = HangRecord.Builder(660, unixTimeMs)
    collector.onRecordingStopped(interval, builder)
    assertThat(builder.build().samples).hasSize(10)
    assertThat(readFile()).isEmpty()
  }

  @Test
  fun `onRecordingStarted opens the profile file before the first sample`() {
    startInterval()

    assertThat(file().exists()).isTrue()
  }

  @Test
  fun `at 700ms file is cleared then buffer and new samples are written`() {
    seedFile(1)
    startInterval()
    sampleEvery66Ms(11) // 0..660 ms
    assertThat(AnrProfileManager(options, file()).use { it.load().stacks }.map { it.timestampMs })
      .containsExactly(1L)

    collector.onSample(interval) // 726 ms: first one past the threshold
    advance(66)
    collector.onSample(interval) // 792 ms

    val timestamps = readFile().map { it.timestampMs }
    assertThat(timestamps).hasSize(13)
    assertThat(timestamps).doesNotContain(1L)
    assertThat(timestamps).isInOrder()
    assertThat(timestamps.first()).isEqualTo(1_700_000_000_000L)
  }

  @Test
  fun `stops sampling when the file is full but keeps samples`() {
    startInterval()
    sampleEvery66Ms(150)

    val builder = HangRecord.Builder(0, unixTimeMs)
    collector.onRecordingStopped(interval, builder)
    assertThat(builder.build().samples).hasSize(120)
    val timestamps = readFile().map { it.timestampMs }
    assertThat(timestamps).hasSize(120)
    assertThat(timestamps.first()).isEqualTo(1_700_000_000_000L)
  }

  @Test
  fun `onRecordingStopped puts samples and thread state into record`() {
    val parked = Thread { while (!Thread.currentThread().isInterrupted) LockSupport.park() }
    parked.isDaemon = true
    parked.start()
    try {
      val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while (parked.state != Thread.State.WAITING && System.nanoTime() < deadlineNanos) {
        Thread.yield()
      }
      assertThat(parked.state).isEqualTo(Thread.State.WAITING)
      collector = ThreadSampleCollector(options, epochClock, ticker, parked)
      startInterval()
      collector.onSample(interval)

      val builder = HangRecord.Builder(66, unixTimeMs)
      collector.onRecordingStopped(interval, builder)
      val sample = builder.build().samples.single()
      assertThat(sample.state).isEqualTo(Thread.State.WAITING)
      assertThat(sample.timestampMs).isEqualTo(unixTimeMs)
      assertThat(sample.stack).isNotEmpty()
    } finally {
      parked.interrupt()
      parked.join()
    }
  }

  @Test
  fun `onRecordingCanceled drops the samples in memory but keeps the file`() {
    startInterval()
    sampleEvery66Ms(12)

    collector.onRecordingCanceled()

    val builder = HangRecord.Builder(0, unixTimeMs)
    collector.onRecordingStopped(interval, builder)
    assertThat(builder.build().samples).isEmpty()
    assertThat(readFile()).hasSize(12)
  }

  @Test
  fun `interval after onRecordingCanceled clears the file once`() {
    startInterval()
    sampleEvery66Ms(12)
    collector.onRecordingCanceled()
    startInterval()
    sampleEvery66Ms(12)

    val timestamps = readFile().map { it.timestampMs }
    assertThat(timestamps).hasSize(12)
    assertThat(timestamps.first()).isAtLeast(1_700_000_000_000L + 12 * 66)
  }

  @Test
  fun `second interval clears the file once`() {
    startInterval()
    sampleEvery66Ms(12)
    startInterval()
    sampleEvery66Ms(12)

    val timestamps = readFile().map { it.timestampMs }
    assertThat(timestamps).hasSize(12)
    assertThat(timestamps.first()).isAtLeast(1_700_000_000_000L + 12 * 66)
  }

  @Test
  fun `close drops the samples in memory`() {
    startInterval()
    sampleEvery66Ms(5)

    collector.close()

    val builder = HangRecord.Builder(0, unixTimeMs)
    collector.onRecordingStopped(interval, builder)
    assertThat(builder.build().samples).isEmpty()
  }

  @Test
  fun `keeps samples in memory when cacheDirPath is null at onRecordingStarted`() {
    options.cacheDirPath = null
    startInterval()
    options.cacheDirPath = cacheDir.absolutePath
    sampleEvery66Ms(15)

    val builder = HangRecord.Builder(0, unixTimeMs)
    collector.onRecordingStopped(interval, builder)
    assertThat(builder.build().samples).hasSize(15)
    assertThat(file().exists()).isFalse()
  }
}
