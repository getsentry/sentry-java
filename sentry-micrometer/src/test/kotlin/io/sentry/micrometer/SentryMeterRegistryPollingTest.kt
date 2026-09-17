package io.sentry.micrometer

import com.google.common.truth.Truth.assertThat
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.TimeGauge
import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.metrics.IMetricsApi
import io.sentry.metrics.MetricsUnit
import io.sentry.metrics.SentryMetricsParameters
import io.sentry.test.initForTest
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SentryMeterRegistryPollingTest {
  private val registries = mutableListOf<SentryMeterRegistry>()

  @BeforeTest
  fun setUp() {
    initForTest { it.dsn = "https://key@sentry.io/proj" }
  }

  @AfterTest
  fun tearDown() {
    registries.forEach(SentryMeterRegistry::close)
    Sentry.close()
  }

  @Test
  fun `schedules fixed-rate polling with the configured interval`() {
    val metrics = installMetricsApi()
    val scheduler = mock<ScheduledExecutorService>()
    val task = mock<ScheduledFuture<Unit>>()
    whenever(
        scheduler.scheduleAtFixedRate(
          any(),
          eq(2500L),
          eq(2500L),
          eq(TimeUnit.MILLISECONDS),
        )
      )
      .thenReturn(task)

    val registry = track(SentryMeterRegistry(2500, Clock.SYSTEM, scheduler))
    val value = AtomicReference(4.5)
    Gauge.builder("scheduled", value) { it.get() }.strongReference(true).register(registry)
    val scheduledPoll = argumentCaptor<Runnable>()
    verify(scheduler)
      .scheduleAtFixedRate(
        scheduledPoll.capture(),
        eq(2500L),
        eq(2500L),
        eq(TimeUnit.MILLISECONDS),
      )

    scheduledPoll.firstValue.run()
    registry.close()
    registry.close()

    verify(metrics).gauge(eq("scheduled"), eq(4.5), anyOrNull(), any())
    verify(task).cancel(true)
    verify(scheduler).shutdownNow()
  }

  @Test
  fun `zero interval disables polling and active meters still forward`() {
    val metrics = installMetricsApi()
    val registry = track(SentryMeterRegistry(0))
    val callbackInvocations = AtomicInteger()
    Gauge.builder("queue.depth", callbackInvocations) {
        callbackInvocations.incrementAndGet().toDouble()
      }
      .register(registry)

    registry.counter("requests").increment()

    assertThat(callbackInvocations.get()).isEqualTo(0)
    verify(metrics).count(eq("requests"), eq(1.0), anyOrNull(), any())
    verify(metrics, never()).gauge(any(), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `zero interval ignores an available scheduler`() {
    val scheduler = mock<ScheduledExecutorService>()
    track(SentryMeterRegistry(0, Clock.SYSTEM, scheduler))

    verifyNoInteractions(scheduler)
  }

  @Test
  fun `negative intervals are rejected`() {
    assertFailsWith<IllegalArgumentException> { SentryMeterRegistry(-1) }
  }

  @Test
  fun `polls gauges with converted metadata`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val value = AtomicReference(4.5)
    Gauge.builder("queue.depth", value) { it.get() }
      .baseUnit("bytes")
      .tag("queue.name", "primary")
      .strongReference(true)
      .register(registry)

    registry.pollMeters()

    val parameters = argumentCaptor<SentryMetricsParameters>()
    verify(metrics)
      .gauge(
        eq("queue.depth"),
        eq(4.5),
        eq(MetricsUnit.Information.BYTE),
        parameters.capture(),
      )
    assertThat(parameters.firstValue.origin).isEqualTo("auto.metrics.micrometer")
    assertThat(parameters.firstValue.attributes!!.attributes["queue.name"]!!.value)
      .isEqualTo("primary")
  }

  @Test
  fun `each passive poll resolves the current scopes metrics API`() {
    val first = mock<IMetricsApi>()
    val second = mock<IMetricsApi>()
    val registry = pollingRegistry()
    val value = AtomicReference(4.5)
    Gauge.builder("queue.depth", value) { it.get() }.strongReference(true).register(registry)

    installMetricsApi(first)
    registry.pollMeters()
    installMetricsApi(second)
    value.set(5.5)
    registry.pollMeters()

    verify(first).gauge(eq("queue.depth"), eq(4.5), anyOrNull(), any())
    verify(second).gauge(eq("queue.depth"), eq(5.5), anyOrNull(), any())
  }

  @Test
  fun `polls time gauges as milliseconds`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val seconds = AtomicReference(1.5)
    TimeGauge.builder("job.duration", seconds, TimeUnit.SECONDS) { it.get() }
      .strongReference(true)
      .register(registry)

    registry.pollMeters()

    verify(metrics)
      .gauge(
        eq("job.duration"),
        eq(1500.0),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
  }

  @Test
  fun `polls long task timer active tasks and duration`() {
    val metrics = installMetricsApi()
    val clock = MockClock()
    val registry = pollingRegistry(clock)
    val timer = LongTaskTimer.builder("background.jobs").register(registry)
    val sample = timer.start()
    clock.add(Duration.ofMillis(1250))

    registry.pollMeters()

    verify(metrics).gauge(eq("background.jobs.active"), eq(1.0), anyOrNull(), any())
    verify(metrics)
      .gauge(
        eq("background.jobs.duration"),
        eq(1250.0),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
    sample.stop()
  }

  @Test
  fun `skips non-finite passive values`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val nan = AtomicReference(Double.NaN)
    val infinity = AtomicReference(Double.POSITIVE_INFINITY)
    Gauge.builder("nan", nan) { it.get() }.strongReference(true).register(registry)
    TimeGauge.builder("infinity", infinity, TimeUnit.SECONDS) { it.get() }
      .strongReference(true)
      .register(registry)

    registry.pollMeters()

    verifyNoInteractions(metrics)
  }

  @Test
  fun `one failing function callback does not suppress other passive meters`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val badValue = AtomicReference(1.0)
    val goodValue = AtomicReference(2.0)
    FunctionCounter.builder("bad", badValue) { throw IllegalStateException("failed") }
      .register(registry)
    Gauge.builder("good", goodValue) { it.get() }.strongReference(true).register(registry)

    registry.pollMeters()

    verify(metrics).gauge(eq("good"), eq(2.0), anyOrNull(), any())
    verify(metrics, never()).count(eq("bad"), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `fatal function counter failures are rethrown`() {
    val registry = pollingRegistry()
    val value = AtomicReference(1.0)
    FunctionCounter.builder("fatal", value) { throw OutOfMemoryError("fatal") }.register(registry)

    assertFailsWith<OutOfMemoryError> { registry.pollMeters() }
  }

  @Test
  fun `function counter establishes baseline then emits positive deltas`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val value = AtomicReference(10.0)
    FunctionCounter.builder("completed.jobs", value) { it.get() }
      .baseUnit("jobs")
      .register(registry)

    registry.pollMeters()
    registry.pollMeters()
    verifyNoInteractions(metrics)

    value.set(13.5)
    registry.pollMeters()

    verify(metrics).count(eq("completed.jobs"), eq(3.5), eq("jobs"), any())
  }

  @Test
  fun `function counter callbacks are not invoked during registration`() {
    val registry = pollingRegistry()
    val value = AtomicReference(10.0)
    val callbackInvocations = AtomicInteger()

    FunctionCounter.builder("completed", value) {
        callbackInvocations.incrementAndGet()
        it.get()
      }
      .register(registry)

    assertThat(callbackInvocations.get()).isEqualTo(0)
    registry.pollMeters()
    assertThat(callbackInvocations.get()).isEqualTo(1)
  }

  @Test
  fun `function counter resets establish a new baseline`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val value = AtomicReference(10.0)
    FunctionCounter.builder("completed", value) { it.get() }.register(registry)

    registry.pollMeters()
    value.set(7.0)
    registry.pollMeters()
    value.set(9.0)
    registry.pollMeters()

    verify(metrics).count(eq("completed"), eq(2.0), anyOrNull(), any())
    verify(metrics, times(1)).count(any(), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `function counter waits for first successful finite baseline`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val fail = AtomicBoolean(true)
    val value = AtomicReference(Double.NaN)
    FunctionCounter.builder("completed", value) {
        if (fail.get()) {
          throw IllegalStateException("failed")
        }
        it.get()
      }
      .register(registry)

    registry.pollMeters()
    fail.set(false)
    registry.pollMeters()
    value.set(10.0)
    registry.pollMeters()
    verifyNoInteractions(metrics)

    value.set(12.0)
    registry.pollMeters()

    verify(metrics).count(eq("completed"), eq(2.0), anyOrNull(), any())
  }

  @Test
  fun `removing and re-registering a function counter clears its baseline`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val firstValue = AtomicReference(10.0)
    val first = FunctionCounter.builder("completed", firstValue) { it.get() }.register(registry)
    registry.pollMeters()
    firstValue.set(12.0)
    registry.pollMeters()
    registry.remove(first)

    val secondValue = AtomicReference(100.0)
    FunctionCounter.builder("completed", secondValue) { it.get() }.register(registry)
    registry.pollMeters()
    secondValue.set(105.0)
    registry.pollMeters()

    verify(metrics).count(eq("completed"), eq(2.0), anyOrNull(), any())
    verify(metrics).count(eq("completed"), eq(5.0), anyOrNull(), any())
    verify(metrics, times(2)).count(any(), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `close does not wait for a blocked callback and in-flight poll does not emit`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val callbackStarted = CountDownLatch(1)
    val releaseCallback = CountDownLatch(1)
    val pollFinished = CountDownLatch(1)
    val closeFinished = CountDownLatch(1)
    val value = AtomicReference(1.0)
    Gauge.builder("blocked", value) {
        callbackStarted.countDown()
        releaseCallback.await()
        it.get()
      }
      .strongReference(true)
      .register(registry)

    val pollThread = Thread {
      registry.pollMeters()
      pollFinished.countDown()
    }
    pollThread.start()
    try {
      assertThat(callbackStarted.await(1, TimeUnit.SECONDS)).isTrue()

      Thread {
          registry.close()
          closeFinished.countDown()
        }
        .start()
      assertThat(closeFinished.await(1, TimeUnit.SECONDS)).isTrue()
    } finally {
      releaseCallback.countDown()
      pollThread.join(1000)
    }

    assertThat(pollFinished.count).isEqualTo(0)
    verifyNoInteractions(metrics)
  }

  @Test
  fun `closed registry does not invoke passive callbacks`() {
    val registry = pollingRegistry()
    val callbackInvocations = AtomicInteger()
    Gauge.builder("gauge", callbackInvocations) {
        callbackInvocations.incrementAndGet().toDouble()
      }
      .strongReference(true)
      .register(registry)

    registry.close()
    registry.pollMeters()

    assertThat(callbackInvocations.get()).isEqualTo(0)
  }

  private fun pollingRegistry(clock: Clock = Clock.SYSTEM): SentryMeterRegistry {
    val scheduler = mock<ScheduledExecutorService>()
    val task = mock<ScheduledFuture<Unit>>()
    whenever(scheduler.scheduleAtFixedRate(any(), any(), any(), any())).thenReturn(task)
    return track(SentryMeterRegistry(60_000, clock, scheduler))
  }

  private fun track(registry: SentryMeterRegistry): SentryMeterRegistry {
    registries.add(registry)
    return registry
  }

  private fun installMetricsApi(metrics: IMetricsApi = mock()): IMetricsApi {
    val scopes = mock<IScopes>()
    whenever(scopes.metrics()).thenReturn(metrics)
    whenever(scopes.options).thenReturn(SentryOptions())
    Sentry.setCurrentScopes(scopes)
    return metrics
  }
}
