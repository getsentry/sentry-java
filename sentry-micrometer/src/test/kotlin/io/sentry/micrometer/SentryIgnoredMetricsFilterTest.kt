package io.sentry.micrometer

import com.google.common.truth.Truth.assertThat
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.FunctionTimer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.TimeGauge
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.config.NamingConvention
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.sentry.IScopes
import io.sentry.ISentryClient
import io.sentry.Sentry
import io.sentry.SentryMetricsEvent
import io.sentry.SentryOptions
import io.sentry.metrics.IMetricsApi
import io.sentry.test.createTestScopes
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SentryIgnoredMetricsFilterTest {
  private val options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
  private val client = mock<ISentryClient>()
  private lateinit var registry: SentryMeterRegistry

  @BeforeTest
  fun setUp() {
    whenever(client.isEnabled).thenReturn(true)
    val scopes = createTestScopes(options)
    scopes.bindClient(client)
    Sentry.setCurrentScopes(scopes)
    registry = SentryMeterRegistry(0)
  }

  @AfterTest
  fun tearDown() {
    registry.close()
    Sentry.close()
  }

  @Test
  fun `logging metrics are not ignored by default`() {
    registry.counter("logback.events").increment()
    registry.counter("log4j2.events").increment()
    val events = argumentCaptor<SentryMetricsEvent>()
    verify(client, times(2)).captureMetric(events.capture(), any(), anyOrNull())
    assertThat(events.allValues.map { it.name }).containsExactly("logback.events", "log4j2.events")
  }

  @Test
  fun `denied counter never invokes the metrics API even under a log flood`() {
    options.metrics.addIgnoredMetric("LOGBACK.EVENTS")
    val api = mock<IMetricsApi>()
    val scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(options)
    whenever(scopes.metrics()).thenReturn(api)
    Sentry.setCurrentScopes(scopes)

    val counter = registry.counter("logback.events")
    repeat(100_000) { counter.increment() }

    assertThat(registry.meters).isEmpty()
    verifyNoInteractions(api)
  }

  @Test
  fun `ignored active and passive meters are not registered or evaluated`() {
    options.metrics.addIgnoredMetric("ignored[.].*")
    val reads = AtomicInteger()
    val state = AtomicInteger()
    registry.counter("ignored.counter").increment()
    registry.timer("ignored.timer").record(1, TimeUnit.SECONDS)
    registry.summary("ignored.summary").record(1.0)
    Gauge.builder("ignored.gauge", state) { reads.incrementAndGet().toDouble() }.register(registry)
    TimeGauge.builder("ignored.time", state, TimeUnit.SECONDS) {
        reads.incrementAndGet().toDouble()
      }
      .register(registry)
    FunctionCounter.builder("ignored.function", state) { reads.incrementAndGet().toDouble() }
      .register(registry)
    FunctionTimer.builder(
        "ignored.function.timer",
        state,
        { reads.incrementAndGet().toLong() },
        { reads.incrementAndGet().toDouble() },
        TimeUnit.SECONDS,
      )
      .register(registry)
    LongTaskTimer.builder("ignored.long.timer").register(registry).start().stop()
    registry.pollMeters()

    assertThat(registry.meters).isEmpty()
    assertThat(reads.get()).isEqualTo(0)
    verify(client, never()).captureMetric(any(), any(), anyOrNull())
  }

  @Test
  fun `filter matches the final mapped and convention-converted name`() {
    options.metrics.addIgnoredMetric("export_logback_events")
    registry.config().namingConvention(NamingConvention.snakeCase)
    registry
      .config()
      .meterFilter(
        object : MeterFilter {
          override fun map(id: Meter.Id): Meter.Id = id.withName("export." + id.name)
        }
      )

    registry.counter("logback.events").increment()

    assertThat(registry.meters).isEmpty()
    verify(client, never()).captureMetric(any(), any(), anyOrNull())
  }

  @Test
  fun `filter does not match the raw name when convention changes the export`() {
    options.metrics.addIgnoredMetric("logback.events")
    registry
      .config()
      .namingConvention(
        object : NamingConvention {
          override fun name(name: String, type: Meter.Type, baseUnit: String?) = "export_$name"
        }
      )
    registry.counter("logback.events").increment()
    val event = argumentCaptor<SentryMetricsEvent>()
    verify(client).captureMetric(event.capture(), any(), anyOrNull())
    assertThat(event.firstValue.name).isEqualTo("export_logback.events")
  }

  @Test
  fun `ignoring a timer base name preserves function timer outputs`() {
    options.metrics.addIgnoredMetric("task")
    val state = AtomicInteger()
    FunctionTimer.builder(
        "task",
        state,
        { it.get().toLong() },
        { it.get().toDouble() },
        TimeUnit.SECONDS,
      )
      .register(registry)
    registry.pollMeters()
    state.set(2)
    registry.pollMeters()
    val events = argumentCaptor<SentryMetricsEvent>()
    verify(client, times(2)).captureMetric(events.capture(), any(), anyOrNull())
    assertThat(events.allValues.map { it.name }).containsExactly("task.count", "task.total_time")
  }

  @Test
  fun `ignoring function timer suffixes preserves ordinary timers`() {
    options.metrics.setIgnoredMetrics(listOf("task.count", "task.total_time"))
    registry.timer("task").record(1, TimeUnit.SECONDS)
    val event = argumentCaptor<SentryMetricsEvent>()
    verify(client).captureMetric(event.capture(), any(), anyOrNull())
    assertThat(event.firstValue.name).isEqualTo("task")
  }

  @Test
  fun `core filter drops only the ignored function timer output`() {
    options.metrics.addIgnoredMetric("task.count")
    val state = AtomicInteger()
    FunctionTimer.builder(
        "task",
        state,
        { it.get().toLong() },
        { it.get().toDouble() },
        TimeUnit.SECONDS,
      )
      .register(registry)
    registry.pollMeters()
    state.set(2)
    registry.pollMeters()
    val event = argumentCaptor<SentryMetricsEvent>()
    verify(client).captureMetric(event.capture(), any(), anyOrNull())
    assertThat(event.firstValue.name).isEqualTo("task.total_time")
    assertThat(event.firstValue.value).isEqualTo(2000.0)
  }

  @Test
  fun `core filter drops only the ignored long task timer output`() {
    options.metrics.addIgnoredMetric("task.active")
    LongTaskTimer.builder("task").register(registry).start()
    registry.pollMeters()
    val event = argumentCaptor<SentryMetricsEvent>()
    verify(client).captureMetric(event.capture(), any(), anyOrNull())
    assertThat(event.firstValue.name).isEqualTo("task.duration")
  }

  @Test
  fun `long task timer is denied when both outputs are ignored without matching its base name`() {
    options.metrics.setIgnoredMetrics(listOf("task.active", "task.duration"))
    LongTaskTimer.builder("task").register(registry).start()
    assertThat(registry.meters).isEmpty()
  }

  @Test
  fun `rules added after registration still apply through the core filter`() {
    val counter = registry.counter("logback.events")
    options.metrics.addIgnoredMetric("logback.events")
    counter.increment()
    verify(client, never()).captureMetric(any(), any(), anyOrNull())
    assertThat(counter.count()).isEqualTo(1.0)
    options.metrics.setIgnoredMetrics(emptyList())
    counter.increment()
    verify(client).captureMetric(any(), any(), anyOrNull())
  }

  @Test
  fun `ignored metrics affect only the Sentry registry in a composite`() {
    options.metrics.addIgnoredMetric("logback.events")
    val other = SimpleMeterRegistry()
    val composite = CompositeMeterRegistry()
    try {
      composite.add(registry)
      composite.add(other)
      composite.counter("logback.events").increment(2.0)
      assertThat(other.counter("logback.events").count()).isEqualTo(2.0)
      assertThat(registry.meters).isEmpty()
      verify(client, never()).captureMetric(any(), any(), anyOrNull())
    } finally {
      composite.close()
      other.close()
    }
  }

  @Test
  fun `time meter filters use the base unit applied after registration filtering`() {
    registry
      .config()
      .namingConvention(
        object : NamingConvention {
          override fun name(name: String, type: Meter.Type, baseUnit: String?) = "${name}_$baseUnit"
        }
      )
    options.metrics.addIgnoredMetric("blocked_milliseconds.*")
    registry.timer("blocked").record(1, TimeUnit.SECONDS)
    LongTaskTimer.builder("blocked").register(registry).start()
    assertThat(registry.meters).isEmpty()

    options.metrics.setIgnoredMetrics(listOf("allowed_null.*"))
    registry.timer("allowed").record(1, TimeUnit.SECONDS)
    val event = argumentCaptor<SentryMetricsEvent>()
    verify(client).captureMetric(event.capture(), any(), anyOrNull())
    assertThat(event.firstValue.name).isEqualTo("allowed_milliseconds")
  }

  @Test
  fun `gauge filter preserves both possible unit-dependent names`() {
    registry
      .config()
      .namingConvention(
        object : NamingConvention {
          override fun name(name: String, type: Meter.Type, baseUnit: String?) = "${name}_$baseUnit"
        }
      )
    options.metrics.setIgnoredMetrics(listOf("time_null", "plain_milliseconds"))
    val state = AtomicInteger(1)
    TimeGauge.builder("time", state, TimeUnit.SECONDS) { it.get().toDouble() }.register(registry)
    Gauge.builder("plain", state) { it.get().toDouble() }.register(registry)
    registry.pollMeters()
    val events = argumentCaptor<SentryMetricsEvent>()
    verify(client, times(2)).captureMetric(events.capture(), any(), anyOrNull())
    assertThat(events.allValues.map { it.name }).containsExactly("time_milliseconds", "plain_null")
  }

  @Test
  fun `nonmatching filter remains neutral so user filters can still deny meters`() {
    options.metrics.addIgnoredMetric("other")
    registry.config().meterFilter(MeterFilter.denyNameStartsWith("logback"))
    registry.counter("logback.events").increment()
    assertThat(registry.meters).isEmpty()
  }
}
