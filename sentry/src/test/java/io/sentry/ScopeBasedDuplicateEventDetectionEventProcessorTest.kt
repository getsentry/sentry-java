package io.sentry

import com.google.common.truth.Truth.assertThat
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import io.sentry.time.TestMonotonicTicker
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test

class ScopeBasedDuplicateEventDetectionEventProcessorTest {
  private val ticker = TestMonotonicTicker()
  private val options = SentryOptions()

  @Test
  fun `first capture sends and stores exact mechanism source`() {
    val processor = processor()
    val throwable = RuntimeException()

    assertThat(processor.process(mechanismEvent(throwable, "Logback"), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(throwable, "Spring"), Hint())).isNull()
  }

  @Test
  fun `same throwable and same exact mechanism source sends repeatedly`() {
    val processor = processor()
    val throwable = RuntimeException()

    repeat(3) {
      assertThat(processor.process(mechanismEvent(throwable, "Logback"), Hint())).isNotNull()
    }
  }

  @Test
  fun `different mechanism source drops once and consumes the pair`() {
    val processor = processor()
    val throwable = RuntimeException()

    assertThat(processor.process(mechanismEvent(throwable, "Logback"), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(throwable, "Spring"), Hint())).isNull()
    assertThat(processor.process(mechanismEvent(throwable, "Spring"), Hint())).isNotNull()
  }

  @Test
  fun `reverse mechanism source order drops once`() {
    val processor = processor()
    val throwable = RuntimeException()

    assertThat(processor.process(mechanismEvent(throwable, "Spring"), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(throwable, "Logback"), Hint())).isNull()
  }

  @Test
  fun `direct throwable uses unknown source and sends repeatedly`() {
    val processor = processor()
    val throwable = RuntimeException()

    assertThat(processor.process(SentryEvent(throwable), Hint())).isNotNull()
    assertThat(processor.process(SentryEvent(throwable), Hint())).isNotNull()
  }

  @Test
  fun `null and empty mechanism types use the same unknown source`() {
    val processor = processor()
    val throwable = RuntimeException()

    assertThat(processor.process(mechanismEvent(throwable, null), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(throwable, ""), Hint())).isNotNull()
  }

  @Test
  fun `unknown followed by concrete mechanism drops once`() {
    val processor = processor()
    val throwable = RuntimeException()

    assertThat(processor.process(SentryEvent(throwable), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(throwable, "Spring"), Hint())).isNull()
    assertThat(processor.process(mechanismEvent(throwable, "Spring"), Hint())).isNotNull()
  }

  @Test
  fun `concrete mechanism followed by unknown drops once`() {
    val processor = processor()
    val throwable = RuntimeException()

    assertThat(processor.process(mechanismEvent(throwable, "Spring"), Hint())).isNotNull()
    assertThat(processor.process(SentryEvent(throwable), Hint())).isNull()
  }

  @Test
  fun `distinct logging mechanism types are different sources`() {
    val processor = processor()
    val throwable = RuntimeException()

    assertThat(processor.process(mechanismEvent(throwable, "Logback"), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(throwable, "Log4j2"), Hint())).isNull()
  }

  @Test
  fun `events without throwables send`() {
    assertThat(processor().process(SentryEvent(), Hint())).isNotNull()
  }

  @Test
  fun `direct mechanism wrapper is unwrapped for the map key`() {
    val processor = processor()
    val throwable = RuntimeException()

    val first = mechanismEvent(throwable, "Logback")
    val second = mechanismEvent(throwable, "Spring")

    assertThat(first.getThrowable()).isSameInstanceAs(throwable)
    assertThat(processor.process(first, Hint())).isNotNull()
    assertThat(processor.process(second, Hint())).isNull()
  }

  @Test
  fun `shared nested causes do not deduplicate`() {
    val processor = processor()
    val sharedCause = RuntimeException()
    val first = RuntimeException("first", sharedCause)
    val second = RuntimeException("second", sharedCause)

    assertThat(processor.process(mechanismEvent(first, "Logback"), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(second, "Spring"), Hint())).isNotNull()
  }

  @Test
  fun `static stackless singleton sends for repeated same source captures`() {
    val processor = processor()
    val singleton = StacklessException()

    repeat(3) {
      assertThat(processor.process(mechanismEvent(singleton, "ReactorNetty"), Hint())).isNotNull()
    }
  }

  @Test
  fun `stale candidates send and are replaced`() {
    val processor = processor()
    val throwable = RuntimeException()

    assertThat(processor.process(mechanismEvent(throwable, "Logback"), Hint())).isNotNull()
    ticker.advance(1, TimeUnit.SECONDS)

    assertThat(processor.process(mechanismEvent(throwable, "Spring"), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(throwable, "Logback"), Hint())).isNull()
  }

  @Test
  fun `stale entries are pruned during later processor activity`() {
    val processor = processor(maxTrackedThrowables = 1)
    val staleThrowable = RuntimeException()
    val currentThrowable = RuntimeException()

    assertThat(processor.process(mechanismEvent(staleThrowable, "Logback"), Hint())).isNotNull()
    ticker.advance(1, TimeUnit.SECONDS)
    assertThat(processor.process(mechanismEvent(currentThrowable, "Logback"), Hint())).isNotNull()

    assertThat(processor.process(mechanismEvent(currentThrowable, "Spring"), Hint())).isNull()
  }

  @Test
  fun `capacity exhaustion fails open for new throwable keys`() {
    val processor = processor(maxTrackedThrowables = 1)
    val tracked = RuntimeException()
    val untracked = RuntimeException()

    assertThat(processor.process(mechanismEvent(tracked, "Logback"), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(untracked, "Logback"), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(untracked, "Spring"), Hint())).isNotNull()
    assertThat(processor.process(mechanismEvent(tracked, "Spring"), Hint())).isNull()
  }

  @Test
  fun `cyclic causes are irrelevant`() {
    val processor = processor()
    val throwable = CauseReadingFailsException()

    assertThat(processor.process(mechanismEvent(throwable, "Logback"), Hint())).isNotNull()
  }

  @Test
  fun `concurrent same-source captures all send`() {
    val processor = processor()
    val throwable = RuntimeException()
    val executor = Executors.newFixedThreadPool(8)
    val start = CountDownLatch(1)

    try {
      val results =
        (1..16).map {
          executor.submit<SentryEvent?> {
            start.await()
            processor.process(mechanismEvent(throwable, "Logback"), Hint())
          }
        }
      start.countDown()

      assertThat(results.map { it.get(5, TimeUnit.SECONDS) }).doesNotContain(null)
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun `concurrent different-source captures atomically consume one pair`() {
    val processor = processor()
    val throwable = RuntimeException()
    val executor = Executors.newFixedThreadPool(2)
    val start = CountDownLatch(1)

    try {
      val logback =
        executor.submit<SentryEvent?> {
          start.await()
          processor.process(mechanismEvent(throwable, "Logback"), Hint())
        }
      val spring =
        executor.submit<SentryEvent?> {
          start.await()
          processor.process(mechanismEvent(throwable, "Spring"), Hint())
        }
      start.countDown()

      assertThat(listOf(logback.get(), spring.get()).count { it == null }).isEqualTo(1)
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun `fork preserves configuration and starts with empty state`() {
    val processor = processor(maxTrackedThrowables = 1)
    val throwable = RuntimeException()
    assertThat(processor.process(mechanismEvent(throwable, "Logback"), Hint())).isNotNull()

    val fork = processor.fork() as ScopeBasedDuplicateEventDetectionEventProcessor

    assertThat(fork).isNotSameInstanceAs(processor)
    assertThat(fork.process(mechanismEvent(throwable, "Spring"), Hint())).isNotNull()
    assertThat(fork.process(mechanismEvent(throwable, "Logback"), Hint())).isNull()
  }

  @Test
  fun `duplicate logging occurs outside the state map lock`() {
    val processedWhileLogging = AtomicBoolean(false)
    val logger = BlockingCheckLogger()
    val loggingOptions =
      SentryOptions().apply {
        isDebug = true
        setLogger(logger)
      }
    val processor =
      ScopeBasedDuplicateEventDetectionEventProcessor(
        loggingOptions,
        ticker,
        ScopeBasedDuplicateEventDetectionEventProcessor.DEFAULT_CORRELATION_WINDOW_NANOS,
        ScopeBasedDuplicateEventDetectionEventProcessor.DEFAULT_MAX_TRACKED_THROWABLES,
      )
    logger.onLog = {
      val thread = Thread { processor.process(SentryEvent(RuntimeException()), Hint()) }
      logger.processingThread = thread
      thread.start()
      thread.join(1000)
      processedWhileLogging.set(!thread.isAlive)
    }
    val throwable = RuntimeException()
    processor.process(mechanismEvent(throwable, "Logback"), Hint())

    processor.process(mechanismEvent(throwable, "Spring"), Hint())
    logger.processingThread?.join(5000)

    assertThat(processedWhileLogging.get()).isTrue()
  }

  private fun processor(
    maxTrackedThrowables: Int =
      ScopeBasedDuplicateEventDetectionEventProcessor.DEFAULT_MAX_TRACKED_THROWABLES
  ) =
    ScopeBasedDuplicateEventDetectionEventProcessor(
      options,
      ticker,
      ScopeBasedDuplicateEventDetectionEventProcessor.DEFAULT_CORRELATION_WINDOW_NANOS,
      maxTrackedThrowables,
    )

  private fun mechanismEvent(throwable: Throwable, type: String?): SentryEvent {
    val mechanism = Mechanism().apply { this.type = type }
    return SentryEvent(ExceptionMechanismException(mechanism, throwable, Thread.currentThread()))
  }

  private class StacklessException : RuntimeException(null, null, false, false)

  private class CauseReadingFailsException : RuntimeException() {
    override val cause: Throwable?
      get() = error("The processor must not inspect cause chains")
  }

  private class BlockingCheckLogger : ILogger {
    var onLog: (() -> Unit)? = null
    var processingThread: Thread? = null

    override fun log(level: SentryLevel, message: String, vararg args: Any?) {
      onLog?.invoke()
    }

    override fun log(level: SentryLevel, message: String, throwable: Throwable?) = Unit

    override fun log(
      level: SentryLevel,
      throwable: Throwable?,
      message: String,
      vararg args: Any?,
    ) = Unit

    override fun isEnabled(level: SentryLevel?): Boolean = true
  }
}
