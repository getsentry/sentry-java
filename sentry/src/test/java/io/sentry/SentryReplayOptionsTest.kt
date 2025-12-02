package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SentryReplayOptionsTest {
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
  fun `getNetworkRequestHeaders returns default headers by default`() {
    val options = SentryReplayOptions(false, null)
    assertEquals(
      SentryReplayOptions.getNetworkDetailsDefaultHeaders().size,
      options.networkRequestHeaders.size,
    )

    val headers = options.networkRequestHeaders
    SentryReplayOptions.getNetworkDetailsDefaultHeaders().forEach { defaultHeader ->
      assertEquals(true, headers.contains(defaultHeader))
    }
  }

  @Test
  fun `getNetworkResponseHeaders returns default headers by default`() {
    val options = SentryReplayOptions(false, null)
    assertEquals(
      SentryReplayOptions.getNetworkDetailsDefaultHeaders().size,
      options.networkResponseHeaders.size,
    )

    val headers = options.networkResponseHeaders
    SentryReplayOptions.getNetworkDetailsDefaultHeaders().forEach { defaultHeader ->
      assertEquals(true, headers.contains(defaultHeader))
    }
  }

  @Test
  fun `setNetworkRequestHeaders adds to default headers`() {
    val options = SentryReplayOptions(false, null)
    val additionalHeaders = listOf("X-Custom-Header", "X-Another-Header")

    options.setNetworkRequestHeaders(additionalHeaders)

    assertEquals(
      SentryReplayOptions.getNetworkDetailsDefaultHeaders().size + additionalHeaders.size,
      options.networkRequestHeaders.size,
    )

    val headers = options.networkRequestHeaders
    SentryReplayOptions.getNetworkDetailsDefaultHeaders().forEach { defaultHeader ->
      assertTrue(headers.contains(defaultHeader))
    }
    assertTrue(headers.contains("X-Custom-Header"))
    assertTrue(headers.contains("X-Another-Header"))
  }

  @Test
  fun `setNetworkResponseHeaders adds to default headers`() {
    val options = SentryReplayOptions(false, null)
    val additionalHeaders = listOf("X-Response-Header", "X-Debug-Header")

    options.setNetworkResponseHeaders(additionalHeaders)

    assertEquals(
      SentryReplayOptions.getNetworkDetailsDefaultHeaders().size + additionalHeaders.size,
      options.networkResponseHeaders.size,
    )

    val headers = options.networkResponseHeaders
    SentryReplayOptions.getNetworkDetailsDefaultHeaders().forEach { defaultHeader ->
      assertTrue(headers.contains(defaultHeader))
    }
    assertTrue(headers.contains("X-Response-Header"))
    assertTrue(headers.contains("X-Debug-Header"))
  }
}
