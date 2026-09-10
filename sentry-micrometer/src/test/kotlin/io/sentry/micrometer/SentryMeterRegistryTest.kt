package io.sentry.micrometer

import com.google.common.truth.Truth.assertThat
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Measurement
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Statistic
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.config.NamingConvention
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISentryClient
import io.sentry.Sentry
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryMetricsEvent
import io.sentry.SentryOptions
import io.sentry.metrics.IMetricsApi
import io.sentry.metrics.MetricsUnit
import io.sentry.metrics.SentryMetricsParameters
import io.sentry.test.createTestScopes
import io.sentry.test.initForTest
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SentryMeterRegistryTest {
  @BeforeTest
  fun setUp() {
    initForTest { it.dsn = "https://key@sentry.io/proj" }
  }

  @AfterTest
  fun tearDown() {
    Sentry.close()
  }

  @Test
  fun `counter forwards positive finite increments with converted metadata`() {
    val metrics = installMetricsApi()
    val registry = SentryMeterRegistry()
    val counter =
      Counter.builder("request.count")
        .tags("http.method", "GET")
        .baseUnit("requests")
        .register(registry)

    counter.increment(2.5)

    assertThat(counter.count()).isEqualTo(2.5)
    val parameters = argumentCaptor<SentryMetricsParameters>()
    verify(metrics).count(eq("request_count"), eq(2.5), eq("requests"), parameters.capture())
    assertThat(parameters.firstValue.origin).isEqualTo("auto.metrics.micrometer")
    assertThat(parameters.firstValue.attributes!!.attributes["http_method"]!!.value)
      .isEqualTo("GET")
  }

  @Test
  fun `uses the configured naming convention for names tags and values`() {
    val metrics = installMetricsApi()
    val registry = SentryMeterRegistry()
    registry
      .config()
      .namingConvention(
        object : NamingConvention {
          override fun name(name: String, type: Meter.Type, baseUnit: String?) = "metric_$name"

          override fun tagKey(key: String) = "tag_$key"

          override fun tagValue(value: String) = value.lowercase()
        }
      )
    val counter = Counter.builder("requests").tag("METHOD", "GET").register(registry)

    counter.increment()

    val parameters = argumentCaptor<SentryMetricsParameters>()
    verify(metrics).count(eq("metric_requests"), eq(1.0), anyOrNull(), parameters.capture())
    assertThat(parameters.firstValue.attributes!!.attributes["tag_METHOD"]!!.value).isEqualTo("get")
  }

  @Test
  fun `counter does not forward zero negative or non-finite increments`() {
    val metrics = installMetricsApi()
    val registry = SentryMeterRegistry()
    val counter = registry.counter("counter")

    counter.increment(0.0)
    counter.increment(-1.0)
    counter.increment(Double.NaN)
    counter.increment(Double.POSITIVE_INFINITY)

    verifyNoInteractions(metrics)
  }

  @Test
  fun `timer forwards accepted durations as millisecond distributions`() {
    val metrics = installMetricsApi()
    val registry = SentryMeterRegistry()
    val timer = Timer.builder("request.duration").register(registry)

    timer.record(1500, TimeUnit.MICROSECONDS)
    timer.record(-1, TimeUnit.MILLISECONDS)

    assertThat(timer.count()).isEqualTo(1)
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isWithin(0.0001).of(1.5)
    verify(metrics)
      .distribution(
        eq("request_duration"),
        eq(1.5),
        eq(MetricsUnit.Duration.MILLISECOND),
        any(),
      )
    verify(metrics, never()).count(any(), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `distribution summary forwards scaled finite observations and remains readable`() {
    val metrics = installMetricsApi()
    val registry = SentryMeterRegistry()
    val summary =
      DistributionSummary.builder("payload.size").baseUnit("bytes").scale(2.0).register(registry)

    summary.record(3.0)
    summary.record(Double.POSITIVE_INFINITY)

    assertThat(summary.count()).isEqualTo(2)
    assertThat(summary.totalAmount()).isPositiveInfinity()
    verify(metrics)
      .distribution(eq("payload_size"), eq(6.0), eq(MetricsUnit.Information.BYTE), any())
    verify(metrics, times(1)).distribution(any(), anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `each recording resolves the current scopes metrics API`() {
    val first = mock<IMetricsApi>()
    val second = mock<IMetricsApi>()
    val registry = SentryMeterRegistry()
    val counter = registry.counter("counter")

    installMetricsApi(first)
    counter.increment()
    installMetricsApi(second)
    counter.increment(2.0)

    verify(first).count(eq("counter"), eq(1.0), anyOrNull(), any())
    verify(second).count(eq("counter"), eq(2.0), anyOrNull(), any())
  }

  @Test
  fun `registry created before Sentry init forwards after initialization`() {
    Sentry.close()
    val registry = SentryMeterRegistry()
    val counter = registry.counter("counter")

    counter.increment()
    val metrics = mock<IMetricsApi>()
    initForTest { it.dsn = "https://key@sentry.io/proj" }
    installMetricsApi(metrics)
    counter.increment(2.0)

    verify(metrics).count(eq("counter"), eq(2.0), anyOrNull(), any())
  }

  @Test
  fun `active meters stop forwarding after registry close but retain local behavior`() {
    val metrics = installMetricsApi()
    val registry = SentryMeterRegistry()
    val counter = registry.counter("counter")
    val timer = registry.timer("timer")
    val summary = registry.summary("summary")

    registry.close()
    counter.increment()
    timer.record(1, TimeUnit.MILLISECONDS)
    summary.record(1.0)

    assertThat(counter.count()).isEqualTo(1.0)
    assertThat(timer.count()).isEqualTo(1)
    assertThat(summary.count()).isEqualTo(1)
    verifyNoInteractions(metrics)
  }

  @Test
  fun `global Sentry metrics option disables forwarding`() {
    val client = mock<ISentryClient>()
    whenever(client.isEnabled).thenReturn(true)
    val options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        metrics.isEnabled = false
      }
    val scopes = createTestScopes(options)
    scopes.bindClient(client)
    Sentry.setCurrentScopes(scopes)
    val registry = SentryMeterRegistry()

    registry.counter("counter").increment()

    verify(client, never()).captureMetric(any(), any(), anyOrNull())
  }

  @Test
  fun `capture failures do not affect local meter state`() {
    val client = mock<ISentryClient>()
    whenever(client.isEnabled).thenReturn(true)
    doThrow(IllegalStateException("capture failed"))
      .whenever(client)
      .captureMetric(any<SentryMetricsEvent>(), any(), anyOrNull<Hint>())
    val scopes = createTestScopes(SentryOptions().apply { dsn = "https://key@sentry.io/proj" })
    scopes.bindClient(client)
    Sentry.setCurrentScopes(scopes)
    val registry = SentryMeterRegistry()
    val counter = registry.counter("counter")

    counter.increment(3.0)

    assertThat(counter.count()).isEqualTo(3.0)
  }

  @Test
  fun `registry-local filters do not affect another composite registry`() {
    val metrics = installMetricsApi()
    val sentryRegistry = SentryMeterRegistry()
    sentryRegistry.config().meterFilter(MeterFilter.denyNameStartsWith("denied"))
    val otherRegistry = SimpleMeterRegistry()
    val composite = CompositeMeterRegistry()
    composite.add(sentryRegistry)
    composite.add(otherRegistry)

    composite.counter("denied.counter").increment(4.0)
    composite.counter("allowed.counter").increment(2.0)

    assertThat(otherRegistry.counter("denied.counter").count()).isEqualTo(4.0)
    assertThat(otherRegistry.counter("allowed.counter").count()).isEqualTo(2.0)
    assertThat(sentryRegistry.find("denied.counter").counter()).isNull()
    verify(metrics).count(eq("allowed_counter"), eq(2.0), anyOrNull(), any())
  }

  @Test
  fun `custom meters remain readable and are not forwarded`() {
    val metrics = installMetricsApi()
    val registry = SentryMeterRegistry()
    val meter =
      Meter.builder(
          "custom",
          Meter.Type.OTHER,
          listOf(Measurement(Supplier { 7.0 }, Statistic.VALUE)),
        )
        .register(registry)

    assertThat(meter.measure().single().value).isEqualTo(7.0)
    assertThat(registry.get("custom").meter()).isSameInstanceAs(meter)
    verifyNoInteractions(metrics)
  }

  @Test
  fun `registry construction records integration and package metadata`() {
    SentryMeterRegistry()

    val storage = SentryIntegrationPackageStorage.getInstance()
    assertThat(storage.integrations).contains("Micrometer")
    assertThat(storage.packages.map { it.name }).contains("maven:io.sentry:sentry-micrometer")
  }

  private fun installMetricsApi(metrics: IMetricsApi = mock()): IMetricsApi {
    val scopes = mock<IScopes>()
    whenever(scopes.metrics()).thenReturn(metrics)
    whenever(scopes.options).thenReturn(SentryOptions())
    Sentry.setCurrentScopes(scopes)
    return metrics
  }
}
