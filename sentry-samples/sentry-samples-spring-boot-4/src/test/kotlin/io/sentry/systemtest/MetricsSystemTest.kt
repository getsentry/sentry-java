package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before

class MetricsSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `count metric`() {
    val restClient = testHelper.restClient
    assertEquals("count metric increased", restClient.getCountMetric())
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureMetricsReceived { event, header ->
      testHelper.doesContainMetric(event, "countMetric", "counter", 1.0) &&
        testHelper.doesMetricHaveAttribute(event, "countMetric", "user.type", "admin") &&
        testHelper.doesMetricHaveAttribute(event, "countMetric", "feature.version", 2)
    }
  }

  @Test
  fun `Spring Boot generated HTTP metric is forwarded through Micrometer`() {
    val restClient = testHelper.restClient
    assertTrue(restClient.getActuatorHealth()?.contains("\"status\":\"UP\"") == true)
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureMetricsReceived { event, header ->
      event.items.any { metric ->
        metric.name == "http_server_requests" &&
          metric.type == "distribution" &&
          metric.unit == "millisecond" &&
          metric.attributes?.get("sentry.origin")?.value == "auto.metrics.micrometer"
      } &&
        header.sdkVersion?.integrationSet?.contains("Micrometer") == true &&
        header.sdkVersion?.packageSet?.any {
          it.name == "maven:io.sentry:sentry-micrometer"
        } == true
    }
  }

  @Test
  fun `Spring Boot generated process metric is forwarded through Micrometer polling`() {
    testHelper.ensureMetricsReceived { event, header ->
      event.items.any { metric ->
        metric.name == "process_uptime" &&
          metric.type == "gauge" &&
          metric.unit == "millisecond" &&
          metric.value > 0.0 &&
          metric.attributes?.get("sentry.origin")?.value == "auto.metrics.micrometer"
      } &&
        header.sdkVersion?.integrationSet?.contains("Micrometer") == true &&
        header.sdkVersion?.packageSet?.any {
          it.name == "maven:io.sentry:sentry-micrometer"
        } == true
    }
  }

  @Test
  fun `gauge metric`() {
    val restClient = testHelper.restClient
    assertEquals("gauge metric tracked", restClient.getGaugeMetric(14))
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureMetricsReceived { event, header ->
      testHelper.doesContainMetric(event, "memory.free", "gauge", 14.0)
    }
  }

  @Test
  fun `distribution metric`() {
    val restClient = testHelper.restClient
    assertEquals("distribution metric tracked", restClient.getDistributionMetric(23))
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureMetricsReceived { event, header ->
      testHelper.doesContainMetric(event, "distributionMetric", "distribution", 23.0)
    }
  }
}
