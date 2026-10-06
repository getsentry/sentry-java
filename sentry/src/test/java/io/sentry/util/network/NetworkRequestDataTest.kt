package io.sentry.util.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkRequestDataTest {

  private fun responseDetails(statusCode: Int, size: Long): NetworkRequestData.ResponseDetails =
    NetworkRequestData.ResponseDetails(
      statusCode,
      ReplayNetworkRequestOrResponse(size, null, emptyMap()),
    )

  @Test
  fun `response details are empty until they are set`() {
    val data = NetworkRequestData("GET")

    assertNull(data.getStatusCode())
    assertNull(data.getResponse())
    assertNull(data.getResponseBodySize())
  }

  @Test
  fun `response details are all readable once set`() {
    val data = NetworkRequestData("GET")

    data.setResponseDetails(responseDetails(200, 42L))

    assertEquals(200, data.getStatusCode())
    assertEquals(42L, data.getResponseBodySize())
    assertEquals(42L, data.getResponse()?.getSize())
  }

  @Test
  fun `response details can be filled in after the instance was published`() {
    // A body of unknown length is only known once it has been consumed, which can be after the
    // breadcrumb
    // holding this instance already reached the scope, so the values must stay consistent for a
    // reader that arrives late.
    val data = NetworkRequestData("GET")
    assertNull(data.getStatusCode())
    assertNull(data.getResponse())

    data.setResponseDetails(responseDetails(500, 7L))

    assertEquals(500, data.getStatusCode())
    assertEquals(7L, data.getResponseBodySize())
    assertEquals(7L, data.getResponse()?.getSize())
  }

  @Test
  fun `request details are unaffected`() {
    val data = NetworkRequestData("GET")
    val request = ReplayNetworkRequestOrResponse(11L, null, mapOf("Accept" to "application/json"))

    data.setRequestDetails(request)

    assertEquals(11L, data.getRequestBodySize())
    assertEquals(request, data.getRequest())
  }
}
