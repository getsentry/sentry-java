package io.sentry.util.network

import io.sentry.ILogger
import io.sentry.KeyValueCollectionBehavior
import java.util.LinkedHashMap
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class NetworkDetailCaptureUtilsTest {

  @Test
  fun `createResponse uses originalByteCount when bodySize is unknown`() {
    val logger = mock<ILogger>()
    val jsonBytes = """{"key":"value"}""".toByteArray()

    val result =
      NetworkDetailCaptureUtils.createResponse(
        jsonBytes,
        -1L,
        true,
        { bytes ->
          NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size, logger)
        },
        KeyValueCollectionBehavior.off(),
        { emptyMap() },
      )

    assertEquals(jsonBytes.size.toLong(), result.size)
  }

  @Test
  fun `createResponse keeps explicit bodySize when available`() {
    val logger = mock<ILogger>()
    val jsonBytes = """{"key":"value"}""".toByteArray()

    val result =
      NetworkDetailCaptureUtils.createResponse(
        jsonBytes,
        42L,
        true,
        { bytes ->
          NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size, logger)
        },
        KeyValueCollectionBehavior.off(),
        { emptyMap() },
      )

    assertEquals(42L, result.size)
  }

  @Test
  fun `createResponse keeps null bodySize when body capture is off`() {
    val result =
      NetworkDetailCaptureUtils.createResponse(
        "unused",
        null,
        false,
        { null },
        KeyValueCollectionBehavior.off(),
        { emptyMap() },
      )

    assertNull(result.size)
  }

  @Test
  fun `getCaptureHeaders matches allow list case-insensitively and filters sensitive values`() {
    val allHeaders =
      LinkedHashMap<String, String>().apply {
        put("Content-Type", "application/json")
        put("Authorization", "Bearer token123")
        put("X-Custom-Header", "custom-value")
        put("accept", "application/json")
      }
    val behavior =
      KeyValueCollectionBehavior.allowList(
        "content-type",
        "AUTHORIZATION",
        "x-custom-header",
        "ACCEPT",
      )

    val result = NetworkDetailCaptureUtils.getCaptureHeaders(allHeaders, behavior)

    assertEquals(4, result.size)
    assertEquals("application/json", result["Content-Type"])
    assertEquals("[Filtered]", result["Authorization"])
    assertEquals("custom-value", result["X-Custom-Header"])
    assertEquals("application/json", result["accept"])
    assertTrue(result.containsKey("Content-Type"))
    assertTrue(result.containsKey("Authorization"))
    assertTrue(result.containsKey("X-Custom-Header"))
    assertTrue(result.containsKey("accept"))
  }

  @Test
  fun `getCaptureHeaders handles null allHeaders`() {
    val result =
      NetworkDetailCaptureUtils.getCaptureHeaders(
        null,
        KeyValueCollectionBehavior.allowList("content-type"),
      )

    assertTrue(result.isEmpty())
  }

  @Test
  fun `getCaptureHeaders filters every value for empty allow list`() {
    val result =
      NetworkDetailCaptureUtils.getCaptureHeaders(
        mapOf("Content-Type" to "application/json"),
        KeyValueCollectionBehavior.allowList(),
      )

    assertEquals(mapOf("Content-Type" to "[Filtered]"), result)
  }

  @Test
  fun `getCaptureHeaders applies deny list`() {
    val result =
      NetworkDetailCaptureUtils.getCaptureHeaders(
        mapOf(
          "Content-Type" to "application/json",
          "X-Debug" to "secret",
          "X-Request-Id" to "123",
        ),
        KeyValueCollectionBehavior.denyList("debug"),
      )

    assertEquals("application/json", result["Content-Type"])
    assertEquals("[Filtered]", result["X-Debug"])
    assertEquals("123", result["X-Request-Id"])
  }

  @Test
  fun `getCaptureHeaders applies off mode`() {
    val result =
      NetworkDetailCaptureUtils.getCaptureHeaders(
        mapOf("Content-Type" to "application/json"),
        KeyValueCollectionBehavior.off(),
      )

    assertTrue(result.isEmpty())
  }
}
