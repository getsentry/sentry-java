package io.sentry.spring.boot.jakarta.otelagentintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryMetricsEvents
import io.sentry.test.hasMetric
import io.sentry.test.hasMetricWithAttribute
import io.sentry.test.metrics
import kotlin.test.Test

class MetricsIntegrationTest : IntegrationTest() {

  @Test
  fun `count metric carries the scope's attributes`() {
    assertThat(restTemplate.getForObject("/metric/count", String::class.java))
      .isEqualTo("count metric increased")

    val metrics = awaitMetric("countMetric")
    assertThat(metrics.hasMetric("countMetric", "counter", 1.0)).isTrue()
    assertThat(metrics.hasMetricWithAttribute("countMetric", "user.type", "admin")).isTrue()
    assertThat(metrics.hasMetricWithAttribute("countMetric", "feature.version", 2)).isTrue()
  }

  @Test
  fun `gauge metric`() {
    assertThat(restTemplate.getForObject("/metric/gauge/14", String::class.java))
      .isEqualTo("gauge metric tracked")

    assertThat(awaitMetric("memory.free").hasMetric("memory.free", "gauge", 14.0)).isTrue()
  }

  @Test
  fun `distribution metric`() {
    assertThat(restTemplate.getForObject("/metric/distribution/23", String::class.java))
      .isEqualTo("distribution metric tracked")

    assertThat(
        awaitMetric("distributionMetric").hasMetric("distributionMetric", "distribution", 23.0)
      )
      .isTrue()
  }

  private fun awaitMetric(name: String): SentryMetricsEvents =
    server
      .awaitEnvelope { it.metrics()?.items?.any { metric -> metric.name == name } == true }
      .metrics()!!
}
