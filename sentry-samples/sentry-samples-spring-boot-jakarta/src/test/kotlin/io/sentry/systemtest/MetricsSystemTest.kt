package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
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
      testHelper.doesContainMetric(event, "countMetric", "counter", 1.0)
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
