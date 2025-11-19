package io.sentry.util.network

import java.util.LinkedHashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class NetworkDetailCaptureUtilsTest {

  @Test
  fun `getCaptureHeaders should match headers case-insensitively`() {
    // Setup: allHeaders with mixed case keys
    val allHeaders =
      LinkedHashMap<String, String>().apply {
        put("Content-Type", "application/json")
        put("Authorization", "Bearer token123")
        put("X-Custom-Header", "custom-value")
        put("accept", "application/json")
      }

    // Test: allowedHeaders with different casing
    val allowedHeaders = arrayOf("content-type", "AUTHORIZATION", "x-custom-header", "ACCEPT")

    val result = NetworkDetailCaptureUtils.getCaptureHeaders(allHeaders, allowedHeaders)

    // All headers should be matched despite case differences
    assertEquals(4, result.size)

    // Original casing should be preserved in output
    assertEquals("application/json", result["Content-Type"])
    assertEquals("Bearer token123", result["Authorization"])
    assertEquals("custom-value", result["X-Custom-Header"])
    assertEquals("application/json", result["accept"])

    // Verify keys maintain original casing from allHeaders
    assertTrue(result.containsKey("Content-Type"))
    assertTrue(result.containsKey("Authorization"))
    assertTrue(result.containsKey("X-Custom-Header"))
    assertTrue(result.containsKey("accept"))
  }

  @Test
  fun `getCaptureHeaders should handle null allHeaders`() {
    val allowedHeaders = arrayOf("content-type")

    val result = NetworkDetailCaptureUtils.getCaptureHeaders(null, allowedHeaders)

    assertTrue(result.isEmpty())
  }

  @Test
  fun `getCaptureHeaders should handle empty allowedHeaders`() {
    val allHeaders = mapOf("Content-Type" to "application/json")
    val allowedHeaders = arrayOf<String>()

    val result = NetworkDetailCaptureUtils.getCaptureHeaders(allHeaders, allowedHeaders)

    assertTrue(result.isEmpty())
  }

  @Test
  fun `getCaptureHeaders should only capture allowed headers`() {
    val allHeaders =
      mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer token123",
        "X-Unwanted-Header" to "should-not-appear",
      )

    val allowedHeaders = arrayOf("content-type", "authorization")

    val result = NetworkDetailCaptureUtils.getCaptureHeaders(allHeaders, allowedHeaders)

    assertEquals(2, result.size)
    assertEquals("application/json", result["Content-Type"])
    assertEquals("Bearer token123", result["Authorization"])

    // Unwanted header should not be present
    assertTrue(!result.containsKey("X-Unwanted-Header"))
  }
}
