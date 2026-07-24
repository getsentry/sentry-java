package io.sentry

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryReplayOptionsTest {

  @BeforeTest
  fun setup() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  @Test
  fun `uses medium quality as default`() {
    val replayOptions = SentryReplayOptions(true, null)

    assertEquals(SentryReplayOptions.SentryReplayQuality.MEDIUM, replayOptions.quality)
    assertEquals(75_000, replayOptions.quality.bitRate)
    assertEquals(1.0f, replayOptions.quality.sizeScale)
  }

  @Test
  fun `low quality`() {
    val replayOptions =
      SentryReplayOptions(true, null).apply {
        quality = SentryReplayOptions.SentryReplayQuality.LOW
      }

    assertEquals(50_000, replayOptions.quality.bitRate)
    assertEquals(0.8f, replayOptions.quality.sizeScale)
  }

  @Test
  fun `high quality`() {
    val replayOptions =
      SentryReplayOptions(true, null).apply {
        quality = SentryReplayOptions.SentryReplayQuality.HIGH
      }

    assertEquals(100_000, replayOptions.quality.bitRate)
    assertEquals(1.0f, replayOptions.quality.sizeScale)
  }

  @Test
  fun testDefaultScreenshotStrategy() {
    val options = SentryReplayOptions(false, null)
    assertEquals(ScreenshotStrategyType.PIXEL_COPY, options.getScreenshotStrategy())
  }

  @Test
  fun testSetScreenshotStrategyToCanvas() {
    val options = SentryReplayOptions(false, null)
    options.screenshotStrategy = ScreenshotStrategyType.CANVAS
    assertEquals(ScreenshotStrategyType.CANVAS, options.getScreenshotStrategy())
  }

  @Test
  fun testSetScreenshotStrategyToPixelCopy() {
    val options = SentryReplayOptions(false, null)
    options.screenshotStrategy = ScreenshotStrategyType.PIXEL_COPY
    assertEquals(ScreenshotStrategyType.PIXEL_COPY, options.getScreenshotStrategy())
  }

  // Network Details Options
  // https://docs.sentry.io/platforms/javascript/session-replay/configuration/#network-details

  @Test
  fun `network detail collection overrides default to null`() {
    val options = SentryReplayOptions(false, null)

    assertNull(options.networkCaptureBodies)
    assertNull(options.networkRequestHeaderBehavior)
    assertNull(options.networkResponseHeaderBehavior)
  }

  @Test
  fun `network detail collection overrides accept explicit values`() {
    val options = SentryReplayOptions(false, null)
    val requestBehavior = KeyValueCollectionBehavior.denyList("x-debug")
    val responseBehavior = KeyValueCollectionBehavior.off()

    options.networkCaptureBodies = false
    options.networkRequestHeaderBehavior = requestBehavior
    options.networkResponseHeaderBehavior = responseBehavior

    assertEquals(false, options.networkCaptureBodies)
    assertEquals(requestBehavior, options.networkRequestHeaderBehavior)
    assertEquals(responseBehavior, options.networkResponseHeaderBehavior)

    options.networkCaptureBodies = null
    options.networkRequestHeaderBehavior = null
    options.networkResponseHeaderBehavior = null

    assertNull(options.networkCaptureBodies)
    assertNull(options.networkRequestHeaderBehavior)
    assertNull(options.networkResponseHeaderBehavior)
  }

  @Suppress("DEPRECATION")
  @Test
  fun `legacy network getters preserve defaults when overrides are null`() {
    val options = SentryReplayOptions(false, null)

    assertTrue(options.isNetworkCaptureBodies)
    assertEquals(
      SentryReplayOptions.getNetworkDetailsDefaultHeaders(),
      options.networkRequestHeaders,
    )
    assertEquals(
      SentryReplayOptions.getNetworkDetailsDefaultHeaders(),
      options.networkResponseHeaders,
    )
  }

  @Suppress("DEPRECATION")
  @Test
  fun `legacy header setters create allow list overrides including default headers`() {
    val options = SentryReplayOptions(false, null)

    options.setNetworkRequestHeaders(listOf("X-Custom-Header"))
    options.setNetworkResponseHeaders(listOf("X-Response-Header"))

    assertEquals(
      KeyValueCollectionBehavior.Mode.ALLOW_LIST,
      options.networkRequestHeaderBehavior?.mode,
    )
    assertTrue(options.networkRequestHeaderBehavior!!.terms.contains("Content-Type"))
    assertTrue(options.networkRequestHeaderBehavior!!.terms.contains("X-Custom-Header"))
    assertEquals(
      KeyValueCollectionBehavior.Mode.ALLOW_LIST,
      options.networkResponseHeaderBehavior?.mode,
    )
    assertTrue(options.networkResponseHeaderBehavior!!.terms.contains("Content-Type"))
    assertTrue(options.networkResponseHeaderBehavior!!.terms.contains("X-Response-Header"))
  }

  @Suppress("DEPRECATION")
  @Test
  fun `legacy header setters accept null to restore inheritance`() {
    val options = SentryReplayOptions(false, null)
    options.setNetworkRequestHeaders(listOf("X-Custom-Header"))
    options.setNetworkResponseHeaders(listOf("X-Response-Header"))

    options.setNetworkRequestHeaders(null)
    options.setNetworkResponseHeaders(null)

    assertNull(options.networkRequestHeaderBehavior)
    assertNull(options.networkResponseHeaderBehavior)
  }

  @Suppress("DEPRECATION")
  @Test
  fun `legacy header getters return empty lists for non allow list behavior`() {
    val options = SentryReplayOptions(false, null)

    options.networkRequestHeaderBehavior = KeyValueCollectionBehavior.denyList("x-debug")
    options.networkResponseHeaderBehavior = KeyValueCollectionBehavior.off()

    assertTrue(options.networkRequestHeaders.isEmpty())
    assertTrue(options.networkResponseHeaders.isEmpty())
  }

  @Test
  fun `resolved network options use legacy defaults when data collection is absent`() {
    val options = SentryOptions()
    val replay = options.sessionReplay
    val defaultHeaders =
      KeyValueCollectionBehavior.allowList(
        *SentryReplayOptions.getNetworkDetailsDefaultHeaders().toTypedArray()
      )

    assertTrue(replay.isNetworkRequestBodyCaptureEnabled(options.dataCollectionResolver))
    assertTrue(replay.isNetworkResponseBodyCaptureEnabled(options.dataCollectionResolver))
    assertEquals(
      defaultHeaders,
      replay.resolveNetworkRequestHeaders(options.dataCollectionResolver),
    )
    assertEquals(
      defaultHeaders,
      replay.resolveNetworkResponseHeaders(options.dataCollectionResolver),
    )
  }

  @Test
  fun `resolved network options fall back to data collection when configured`() {
    val options =
      SentryOptions().apply {
        dataCollection.httpBodies = setOf(HttpBodyType.INCOMING_RESPONSE)
        dataCollection.httpHeaders.request = KeyValueCollectionBehavior.denyList("x-debug")
        dataCollection.httpHeaders.response = KeyValueCollectionBehavior.off()
      }
    val replay = options.sessionReplay

    assertFalse(replay.isNetworkRequestBodyCaptureEnabled(options.dataCollectionResolver))
    assertTrue(replay.isNetworkResponseBodyCaptureEnabled(options.dataCollectionResolver))
    assertEquals(
      KeyValueCollectionBehavior.denyList("x-debug"),
      replay.resolveNetworkRequestHeaders(options.dataCollectionResolver),
    )
    assertEquals(
      KeyValueCollectionBehavior.off(),
      replay.resolveNetworkResponseHeaders(options.dataCollectionResolver),
    )
  }

  @Test
  fun `explicit Replay network options take precedence over data collection`() {
    val options =
      SentryOptions().apply {
        dataCollection.httpBodies = emptySet()
        dataCollection.httpHeaders.request = KeyValueCollectionBehavior.off()
        dataCollection.httpHeaders.response = KeyValueCollectionBehavior.off()
        sessionReplay.networkCaptureBodies = true
        sessionReplay.networkRequestHeaderBehavior =
          KeyValueCollectionBehavior.allowList("x-request-id")
        sessionReplay.networkResponseHeaderBehavior = KeyValueCollectionBehavior.denyList("x-debug")
      }
    val replay = options.sessionReplay

    assertTrue(replay.isNetworkRequestBodyCaptureEnabled(options.dataCollectionResolver))
    assertTrue(replay.isNetworkResponseBodyCaptureEnabled(options.dataCollectionResolver))
    assertEquals(
      KeyValueCollectionBehavior.allowList("x-request-id"),
      replay.resolveNetworkRequestHeaders(options.dataCollectionResolver),
    )
    assertEquals(
      KeyValueCollectionBehavior.denyList("x-debug"),
      replay.resolveNetworkResponseHeaders(options.dataCollectionResolver),
    )
  }

  // Custom Masking Integration Tests

  private fun hasCustomMaskingIntegration(): Boolean {
    return SentryIntegrationPackageStorage.getInstance()
      .integrations
      .contains("ReplayCustomMasking")
  }

  @Test
  fun `default options does not add ReplayCustomMasking integration`() {
    SentryReplayOptions(false, null)
    assertFalse(hasCustomMaskingIntegration())
  }

  @Test
  fun `empty options does not add ReplayCustomMasking integration`() {
    SentryReplayOptions(true, null)
    assertFalse(hasCustomMaskingIntegration())
  }

  @Test
  fun `addUnmaskViewClass adds ReplayCustomMasking integration`() {
    val options = SentryReplayOptions(false, null)
    options.addUnmaskViewClass("com.example.MyTextView")
    assertTrue(hasCustomMaskingIntegration())
  }

  @Test
  fun `setMaskViewContainerClass does not add ReplayCustomMasking integration`() {
    val options = SentryReplayOptions(false, null)
    options.setMaskViewContainerClass("com.example.MyContainer")
    assertFalse(hasCustomMaskingIntegration())
  }

  @Test
  fun `setUnmaskViewContainerClass does not add ReplayCustomMasking integration`() {
    val options = SentryReplayOptions(false, null)
    options.setUnmaskViewContainerClass("com.example.MyContainer")
    assertFalse(hasCustomMaskingIntegration())
  }

  @Test
  fun `setMaskAllText true does not set custom integration`() {
    val options = SentryReplayOptions(false, null)
    options.setMaskAllText(true)
    options.setMaskAllImages(true)
    assertFalse(hasCustomMaskingIntegration())
  }

  @Test
  fun `trackCustomMasking only adds integration once`() {
    val options = SentryReplayOptions(false, null)
    options.setMaskAllText(false)
    options.setMaskAllImages(false)
    assertTrue(hasCustomMaskingIntegration())
    assertEquals(
      1,
      SentryIntegrationPackageStorage.getInstance().integrations.count {
        it == "ReplayCustomMasking"
      },
    )
  }

  @Test
  fun `addMaskViewClass adds ReplayCustomMasking integration`() {
    val options = SentryReplayOptions(false, null)
    options.addMaskViewClass("com.example.MySensitiveView")
    assertTrue(hasCustomMaskingIntegration())
  }

  @Test
  fun `setMaskAllText adds ReplayCustomMasking integration`() {
    val options = SentryReplayOptions(false, null)
    options.setMaskAllText(false)
    assertTrue(hasCustomMaskingIntegration())
  }

  @Test
  fun `setMaskAllImages adds ReplayCustomMasking integration`() {
    val options = SentryReplayOptions(false, null)
    options.setMaskAllImages(false)
    assertTrue(hasCustomMaskingIntegration())
  }
}
