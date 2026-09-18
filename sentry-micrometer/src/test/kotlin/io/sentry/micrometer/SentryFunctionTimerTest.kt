package io.sentry.micrometer

import com.google.common.truth.Truth.assertThat
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.FunctionTimer
import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.metrics.IMetricsApi
import io.sentry.metrics.MetricsUnit
import io.sentry.test.initForTest
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

class SentryFunctionTimerTest {
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
  fun `callbacks are not invoked during registration`() {
    val registry = pollingRegistry()
    val state = State(10, 2500.0)
    val countInvocations = AtomicInteger()
    val totalTimeInvocations = AtomicInteger()

    FunctionTimer.builder(
        "requests",
        state,
        {
          countInvocations.incrementAndGet()
          it.count
        },
        {
          totalTimeInvocations.incrementAndGet()
          it.totalTime
        },
        TimeUnit.MILLISECONDS,
      )
      .register(registry)

    assertThat(countInvocations.get()).isEqualTo(0)
    assertThat(totalTimeInvocations.get()).isEqualTo(0)
    registry.pollMeters()
    assertThat(countInvocations.get()).isEqualTo(1)
    assertThat(totalTimeInvocations.get()).isEqualTo(1)
  }

  @Test
  fun `count callback failure does not suppress total time`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val state = State(10, 100.0)
    var countFails = false

    FunctionTimer.builder(
        "requests",
        state,
        {
          if (countFails) {
            throw IllegalStateException("count failed")
          }
          it.count
        },
        { it.totalTime },
        TimeUnit.MILLISECONDS,
      )
      .register(registry)

    registry.pollMeters()
    countFails = true
    state.count = 12
    state.totalTime = 150.0
    registry.pollMeters()

    verify(metrics)
      .count(
        eq("requests.total_time"),
        eq(50.0),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
    verify(metrics, never()).count(eq("requests.count"), any(), anyOrNull(), any())
  }

  @Test
  fun `total time callback failure does not suppress count`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val state = State(10, 100.0)
    var totalTimeFails = false

    FunctionTimer.builder(
        "requests",
        state,
        { it.count },
        {
          if (totalTimeFails) {
            throw IllegalStateException("total time failed")
          }
          it.totalTime
        },
        TimeUnit.MILLISECONDS,
      )
      .register(registry)

    registry.pollMeters()
    totalTimeFails = true
    state.count = 12
    state.totalTime = 150.0
    registry.pollMeters()

    verify(metrics).count(eq("requests.count"), eq(2.0), anyOrNull(), any())
    verify(metrics, never()).count(eq("requests.total_time"), any(), anyOrNull(), any())
  }

  @Test
  fun `fatal callback failures are rethrown`() {
    val registry = pollingRegistry()
    val state = State(10, 100.0)
    val totalTimeInvocations = AtomicInteger()

    FunctionTimer.builder(
        "requests",
        state,
        { throw OutOfMemoryError("fatal") },
        {
          totalTimeInvocations.incrementAndGet()
          it.totalTime
        },
        TimeUnit.MILLISECONDS,
      )
      .register(registry)

    assertFailsWith<OutOfMemoryError> { registry.pollMeters() }
    assertThat(totalTimeInvocations.get()).isEqualTo(0)
  }

  @Test
  fun `establishes baselines then emits count and millisecond deltas`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val state = State(10, 2.5)
    registerTimer(registry, "request.duration", state, TimeUnit.SECONDS)

    registry.pollMeters()
    registry.pollMeters()
    verifyNoInteractions(metrics)

    state.count = 13
    state.totalTime = 4.0
    registry.pollMeters()

    verify(metrics).count(eq("request.duration.count"), eq(3.0), anyOrNull(), any())
    verify(metrics)
      .count(
        eq("request.duration.total_time"),
        eq(1500.0),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
    verify(metrics, never()).gauge(any(), anyOrNull(), anyOrNull(), any())
    verify(metrics, never()).distribution(any(), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `count and total time reset independently`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val state = State(10, 1000.0)
    registerTimer(registry, "requests", state)
    registry.pollMeters()

    state.count = 7
    state.totalTime = 1300.0
    registry.pollMeters()
    state.count = 9
    state.totalTime = 200.0
    registry.pollMeters()
    state.totalTime = 250.0
    registry.pollMeters()

    verify(metrics).count(eq("requests.count"), eq(2.0), anyOrNull(), any())
    verify(metrics)
      .count(
        eq("requests.total_time"),
        eq(300.0),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
    verify(metrics)
      .count(
        eq("requests.total_time"),
        eq(50.0),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
    verify(metrics, times(3)).count(any(), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `count can advance while total time waits for its first finite baseline`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val state = State(10, Double.NaN)
    registerTimer(registry, "requests", state)
    registry.pollMeters()

    state.count = 12
    state.totalTime = 100.0
    registry.pollMeters()
    state.count = 15
    state.totalTime = 150.0
    registry.pollMeters()

    verify(metrics).count(eq("requests.count"), eq(2.0), anyOrNull(), any())
    verify(metrics).count(eq("requests.count"), eq(3.0), anyOrNull(), any())
    verify(metrics)
      .count(
        eq("requests.total_time"),
        eq(50.0),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
    verify(metrics, times(3)).count(any(), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `non-finite total time leaves its baseline unchanged`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val state = State(10, 100.0)
    registerTimer(registry, "requests", state)
    registry.pollMeters()

    state.count = 12
    state.totalTime = Double.NaN
    registry.pollMeters()
    state.totalTime = 150.0
    registry.pollMeters()

    verify(metrics).count(eq("requests.count"), eq(2.0), anyOrNull(), any())
    verify(metrics)
      .count(
        eq("requests.total_time"),
        eq(50.0),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
    verify(metrics, times(2)).count(any(), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `removing and re-registering clears both baselines`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val firstState = State(10, 100.0)
    val first = registerTimer(registry, "requests", firstState)
    registry.pollMeters()
    firstState.count = 12
    firstState.totalTime = 150.0
    registry.pollMeters()
    registry.remove(first)

    val secondState = State(100, 1000.0)
    registerTimer(registry, "requests", secondState)
    registry.pollMeters()
    secondState.count = 105
    secondState.totalTime = 1200.0
    registry.pollMeters()

    verify(metrics).count(eq("requests.count"), eq(2.0), anyOrNull(), any())
    verify(metrics).count(eq("requests.count"), eq(5.0), anyOrNull(), any())
    verify(metrics)
      .count(
        eq("requests.total_time"),
        eq(50.0),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
    verify(metrics)
      .count(
        eq("requests.total_time"),
        eq(200.0),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
    verify(metrics, times(4)).count(any(), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `count and total time produce a weighted mean across instances`() {
    val metrics = installMetricsApi()
    val registry = pollingRegistry()
    val firstState = State(10, 1000.0)
    val secondState = State(20, 2000.0)
    registerTimer(registry, "requests", firstState, tagValue = "first")
    registerTimer(registry, "requests", secondState, tagValue = "second")
    registry.pollMeters()

    firstState.count += 2
    firstState.totalTime += 300.0
    secondState.count += 3
    secondState.totalTime += 900.0
    registry.pollMeters()

    val names = argumentCaptor<String>()
    val values = argumentCaptor<Double>()
    verify(metrics, times(4)).count(names.capture(), values.capture(), anyOrNull(), any())
    val emitted = names.allValues.zip(values.allValues)
    val totalCount = emitted.filter { it.first == "requests.count" }.sumOf { it.second }
    val totalTime = emitted.filter { it.first == "requests.total_time" }.sumOf { it.second }

    assertThat(totalTime / totalCount).isEqualTo(240.0)
  }

  private fun registerTimer(
    registry: SentryMeterRegistry,
    name: String,
    state: State,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
    tagValue: String? = null,
  ): FunctionTimer {
    val builder = FunctionTimer.builder(name, state, { it.count }, { it.totalTime }, unit)
    if (tagValue != null) {
      builder.tag("instance", tagValue)
    }
    return builder.register(registry)
  }

  private fun pollingRegistry(): SentryMeterRegistry {
    val scheduler = mock<ScheduledExecutorService>()
    val task = mock<ScheduledFuture<Unit>>()
    whenever(scheduler.scheduleAtFixedRate(any(), any(), any(), any())).thenReturn(task)
    return SentryMeterRegistry(60_000, Clock.SYSTEM, scheduler).also(registries::add)
  }

  private fun installMetricsApi(): IMetricsApi {
    val metrics = mock<IMetricsApi>()
    val scopes = mock<IScopes>()
    whenever(scopes.metrics()).thenReturn(metrics)
    whenever(scopes.options).thenReturn(SentryOptions())
    Sentry.setCurrentScopes(scopes)
    return metrics
  }

  private data class State(var count: Long, var totalTime: Double)
}
