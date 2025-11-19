package io.sentry.rrweb

import io.sentry.ILogger
import io.sentry.SentryOptions
import io.sentry.SentryReplayOptions.SentryReplayQuality.LOW
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SerializationUtils
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class RRWebOptionsEventSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      RRWebOptionsEvent(
          SentryOptions().apply {
            sdkVersion = SdkVersion("sentry.java", "7.19.1")
            sessionReplay.sessionSampleRate = 0.5
            sessionReplay.onErrorSampleRate = 0.1
            sessionReplay.quality = LOW
            sessionReplay.unmaskViewClasses.add("com.example.MyClass")
            sessionReplay.maskViewClasses.clear()
          }
        )
        .apply { timestamp = 12345678901 }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = SerializationUtils.sanitizedFile("json/rrweb_options_event.json")
    val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = SerializationUtils.sanitizedFile("json/rrweb_options_event.json")
    val actual =
      SerializationUtils.deserializeJson(
        expectedJson,
        RRWebOptionsEvent.Deserializer(),
        fixture.logger,
      )
    val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)
    assertEquals(expectedJson, actualJson)
  }

  @Test
  fun `network detail fields are not included when networkDetailAllowUrls is empty`() {
    val options =
      SentryOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(emptyArray())

        // Any config is ignored when no allowUrls are specified.
        sessionReplay.setNetworkDetailDenyUrls(arrayOf("https://internal.example.com/*"))
        sessionReplay.setNetworkRequestHeaders(listOf("Authorization", "X-Custom"))
        sessionReplay.setNetworkResponseHeaders(listOf("X-RateLimit", "Content-Type"))
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertFalse(payload.containsKey("networkDetailAllowUrls"))
    assertFalse(payload.containsKey("networkDetailDenyUrls"))
    assertFalse(payload.containsKey("networkRequestHeaders"))
    assertFalse(payload.containsKey("networkResponseHeaders"))
    assertFalse(payload.containsKey("networkCaptureBodies"))
    assertEquals(false, payload["networkDetailHasUrls"])
  }

  @Test
  fun `networkDetailAllowUrls and headers are included when networkDetailAllowUrls is configured`() {
    val options =
      SentryOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(arrayOf("https://api.example.com/*"))
        sessionReplay.setNetworkRequestHeaders(listOf("Authorization", "X-Custom"))
        sessionReplay.setNetworkResponseHeaders(listOf("X-RateLimit", "Content-Type"))
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertTrue(payload.containsKey("networkDetailAllowUrls"))
    assertTrue(payload.containsKey("networkRequestHeaders"))
    assertTrue(payload.containsKey("networkResponseHeaders"))
    assertEquals(true, payload["networkDetailHasUrls"])
    assertContentEquals(
      arrayOf("https://api.example.com/*"),
      payload["networkDetailAllowUrls"] as Array<String>,
    )
    assertContentEquals(
      arrayOf("Content-Type", "Content-Length", "Accept", "Authorization", "X-Custom"),
      payload["networkRequestHeaders"] as Array<String>,
    )
    assertContentEquals(
      arrayOf("Content-Type", "Content-Length", "Accept", "X-RateLimit"),
      payload["networkResponseHeaders"] as Array<String>,
    )
  }

  @Test
  fun `networkDetailDenyUrls are included when networkDetailAllowUrls is configured`() {
    val options =
      SentryOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(arrayOf("https://api.example.com/*"))
        sessionReplay.setNetworkDetailDenyUrls(arrayOf("https://internal.example.com/*"))
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertTrue(payload.containsKey("networkDetailAllowUrls"))
    assertTrue(payload.containsKey("networkDetailDenyUrls"))
    assertContentEquals(
      arrayOf("https://api.example.com/*"),
      payload["networkDetailAllowUrls"] as Array<String>,
    )
    assertContentEquals(
      arrayOf("https://internal.example.com/*"),
      payload["networkDetailDenyUrls"] as Array<String>,
    )
  }

  @Test
  fun `networkCaptureBodies is included when networkDetailAllowUrls is configured`() {
    val options =
      SentryOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(arrayOf("https://api.example.com/*"))
        sessionReplay.setNetworkCaptureBodies(false)
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertTrue(payload.containsKey("networkCaptureBodies"))
    assertEquals(false, payload["networkCaptureBodies"])
  }

  @Test
  fun `default networkCaptureBodies is included when networkDetailAllowUrls is configured`() {
    val options =
      SentryOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(arrayOf("https://api.example.com/*"))
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertTrue(payload.containsKey("networkCaptureBodies"))
    assertEquals(true, payload["networkCaptureBodies"])
  }

  @Test
  fun `default network request and response headers are included when networkDetailAllowUrls is configured but no custom headers set`() {
    val options =
      SentryOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(arrayOf("https://api.example.com/*"))
        // No custom headers set, should use defaults only
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertTrue(payload.containsKey("networkRequestHeaders"))
    assertTrue(payload.containsKey("networkResponseHeaders"))
    assertContentEquals(
      arrayOf("Content-Type", "Content-Length", "Accept"),
      payload["networkRequestHeaders"] as Array<String>,
    )
    assertContentEquals(
      arrayOf("Content-Type", "Content-Length", "Accept"),
      payload["networkResponseHeaders"] as Array<String>,
    )
  }
}
