package io.sentry.util.network

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkRequestDataTest {

  private fun response(statusCode: Int, size: Long): ReplayNetworkRequestOrResponse =
    ReplayNetworkRequestOrResponse(size, null, emptyMap())

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

    data.setResponseDetails(200, response(200, 42L))

    assertEquals(200, data.getStatusCode())
    assertEquals(42L, data.getResponseBodySize())
    assertEquals(42L, data.getResponse()?.getSize())
  }

  @Test
  fun `response details can be filled in after the instance was published`() {
    // A streamed body is only known once it has been consumed, which can be after the breadcrumb
    // holding this instance reached the scope.
    val data = NetworkRequestData("GET")
    assertNull(data.getStatusCode())

    data.setResponseDetails(500, response(500, 7L))

    assertEquals(500, data.getStatusCode())
    assertEquals(7L, data.getResponseBodySize())
  }

  @Test
  fun `a reader never observes a partially updated response`() {
    val data = NetworkRequestData("GET")
    val started = CountDownLatch(1)
    val stop = AtomicBoolean(false)
    var torn = false

    val reader = Thread {
      started.await()
      while (!stop.get()) {
        // status, size and response must move together: a reader that saw the status code before
        // the response would report an inconsistent request in the replay
        if (data.getStatusCode() != null && data.getResponse() == null) {
          torn = true
        }
      }
    }

    reader.start()
    started.countDown()
    repeat(200_000) { data.setResponseDetails(200, response(200, it.toLong())) }
    stop.set(true)
    reader.join(5_000)

    assertTrue(!torn, "observed statusCode without a matching response")
  }
}
