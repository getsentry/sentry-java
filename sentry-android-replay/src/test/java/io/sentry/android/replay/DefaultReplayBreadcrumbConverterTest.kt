package io.sentry.android.replay

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SpanDataConvention
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebSpanEvent
import io.sentry.util.network.NetworkBody
import io.sentry.util.network.NetworkRequestData
import io.sentry.util.network.ReplayNetworkRequestOrResponse
import java.util.Date
import junit.framework.TestCase.assertEquals
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class DefaultReplayBreadcrumbConverterTest {
  class Fixture {
    fun getSut(): DefaultReplayBreadcrumbConverter = DefaultReplayBreadcrumbConverter()
  }

  private val fixture = Fixture()

  @Test
  fun `returns null when no category`() {
    val converter = fixture.getSut()

    val breadcrumb = Breadcrumb(Date(123L)).apply { message = "message" }

    val rrwebEvent = converter.convert(breadcrumb)

    assertNull(rrwebEvent)
  }

  @Test
  fun `convert RRWebSpanEvent`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "http"
        data["url"] = "http://example.com"
        data["status_code"] = 404
        data["method"] = "GET"
        data[SpanDataConvention.HTTP_START_TIMESTAMP] = 1234L
        data[SpanDataConvention.HTTP_END_TIMESTAMP] = 2234L
        data["http.response_content_length"] = 300
        data["http.request_content_length"] = 400
      }

    val rrwebEvent = converter.convert(breadcrumb)

    check(rrwebEvent is RRWebSpanEvent)
    assertEquals("resource.http", rrwebEvent.op)
    assertEquals("http://example.com", rrwebEvent.description)
    assertEquals(123L, rrwebEvent.timestamp)
    assertEquals(1.234, rrwebEvent.startTimestamp)
    assertEquals(2.234, rrwebEvent.endTimestamp)
    assertEquals(404, rrwebEvent.data!!["statusCode"])
    assertEquals("GET", rrwebEvent.data!!["method"])
    assertEquals(300, rrwebEvent.data!!["responseBodySize"])
    assertEquals(400, rrwebEvent.data!!["requestBodySize"])
  }

  @Test
  fun `convert RRWebSpanEvent works with floating timestamps`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "http"
        data["url"] = "http://example.com"
        data["status_code"] = 404
        data["method"] = "GET"
        data[SpanDataConvention.HTTP_START_TIMESTAMP] = 1234.0
        data[SpanDataConvention.HTTP_END_TIMESTAMP] = 2234.0
        data["http.response_content_length"] = 300
        data["http.request_content_length"] = 400
      }

    val rrwebEvent = converter.convert(breadcrumb)

    check(rrwebEvent is RRWebSpanEvent)
    assertEquals(1.234, rrwebEvent.startTimestamp)
    assertEquals(2.234, rrwebEvent.endTimestamp)
  }

  @Test
  fun `returns null if not eligible for RRWebSpanEvent`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "http"
        data["status_code"] = 404
        data["method"] = "GET"
        data[SpanDataConvention.HTTP_START_TIMESTAMP] = 1234L
        data[SpanDataConvention.HTTP_END_TIMESTAMP] = 2234L
        data["http.response_content_length"] = 300
        data["http.request_content_length"] = 400
      }

    val rrwebEvent = converter.convert(breadcrumb)

    assertNull(rrwebEvent)
  }

  @Test
  fun `converts app lifecycle breadcrumbs`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "app.lifecycle"
        type = "navigation"
        data["state"] = "background"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    check(rrwebEvent is RRWebBreadcrumbEvent)
    assertEquals("app.background", rrwebEvent.category)
    assertEquals(123L, rrwebEvent.timestamp)
    assertEquals(0.123, rrwebEvent.breadcrumbTimestamp)
    assertEquals("default", rrwebEvent.breadcrumbType)
  }

  @Test
  fun `converts device orientation breadcrumbs`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "device.orientation"
        type = "navigation"
        data["position"] = "landscape"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    check(rrwebEvent is RRWebBreadcrumbEvent)
    assertEquals("device.orientation", rrwebEvent.category)
    assertEquals("landscape", rrwebEvent.data!!["position"])
  }

  @Test
  fun `returns null if no position for orientation breadcrumbs`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "device.orientation"
        type = "navigation"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    assertNull(rrwebEvent)
  }

  @Test
  fun `converts navigation breadcrumbs`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "navigation"
        type = "navigation"
        data["state"] = "resumed"
        data["screen"] = "io.sentry.MainActivity"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    check(rrwebEvent is RRWebBreadcrumbEvent)
    assertEquals("navigation", rrwebEvent.category)
    assertEquals("MainActivity", rrwebEvent.data!!["to"])
  }

  @Test
  fun `converts navigation breadcrumbs with destination`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "navigation"
        type = "navigation"
        data["to"] = "/github"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    check(rrwebEvent is RRWebBreadcrumbEvent)
    assertEquals("navigation", rrwebEvent.category)
    assertEquals("/github", rrwebEvent.data!!["to"])
  }

  @Test
  fun `returns null when lifecycle state is not 'resumed'`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "navigation"
        type = "navigation"
        data["state"] = "started"
        data["screen"] = "io.sentry.MainActivity"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    assertNull(rrwebEvent)
  }

  @Test
  fun `converts ui click breadcrumbs`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "ui.click"
        type = "user"
        data["view.id"] = "button_login"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    check(rrwebEvent is RRWebBreadcrumbEvent)
    assertEquals("ui.tap", rrwebEvent.category)
    assertEquals("button_login", rrwebEvent.message)
  }

  @Test
  fun `returns null if no view identifier in data`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "ui.click"
        type = "user"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    assertNull(rrwebEvent)
  }

  @Test
  fun `converts network connectivity breadcrumbs`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "network.event"
        type = "system"
        data["network_type"] = "cellular"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    check(rrwebEvent is RRWebBreadcrumbEvent)
    assertEquals("device.connectivity", rrwebEvent.category)
    assertEquals("cellular", rrwebEvent.data!!["state"])
  }

  @Test
  fun `returns null if no network connectivity state`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "network.event"
        type = "system"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    assertNull(rrwebEvent)
  }

  @Test
  fun `converts battery status breadcrumbs`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "device.event"
        type = "system"
        data["action"] = "BATTERY_CHANGED"
        data["level"] = 85.0f
        data["charging"] = true
        data["stuff"] = "shiet"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    check(rrwebEvent is RRWebBreadcrumbEvent)
    assertEquals("device.battery", rrwebEvent.category)
    assertEquals(85.0f, rrwebEvent.data!!["level"])
    assertEquals(true, rrwebEvent.data!!["charging"])
    assertNull(rrwebEvent.data!!["stuff"])
  }

  @Test
  fun `converts generic breadcrumbs`() {
    val converter = fixture.getSut()

    val breadcrumb =
      Breadcrumb(Date(123L)).apply {
        category = "device.event"
        type = "system"
        message = "message"
        level = SentryLevel.ERROR
        data["stuff"] = "shiet"
      }

    val rrwebEvent = converter.convert(breadcrumb)

    check(rrwebEvent is RRWebBreadcrumbEvent)
    assertEquals("device.event", rrwebEvent.category)
    assertEquals("message", rrwebEvent.message)
    assertEquals(SentryLevel.ERROR, rrwebEvent.level)
    assertEquals("shiet", rrwebEvent.data!!["stuff"])
  }

  // BeforeBreadcrumbCallback delegation tests

  @Test
  fun `returned breadcrumb is not modified when no user BeforeBreadcrumbCallback is provided`() {
    val converter = fixture.getSut()
    val breadcrumb =
      Breadcrumb(Date()).apply {
        message = "test message"
        category = "test.category"
      }
    val hint = Hint()

    converter.setUserBeforeBreadcrumbCallback(null)

    val result = converter.execute(breadcrumb, hint)

    assertSame(breadcrumb, result)
  }

  @Test
  fun `returned breadcrumb is modified according to user provided BeforeBreadcrumbCallback`() {
    val converter = fixture.getSut()
    val originalBreadcrumb =
      Breadcrumb(Date()).apply {
        message = "original message"
        category = "original.category"
      }
    val userModifiedBreadcrumb =
      Breadcrumb(Date()).apply {
        message = "modified message"
        category = "modified.category"
      }
    val hint = Hint()

    val userBeforeBreadcrumbCallback =
      SentryOptions.BeforeBreadcrumbCallback { _, _ -> userModifiedBreadcrumb }
    converter.setUserBeforeBreadcrumbCallback(userBeforeBreadcrumbCallback)

    val result = converter.execute(originalBreadcrumb, hint)

    assertSame(userModifiedBreadcrumb, result)
  }

  @Test
  fun `returns null when user BeforeBreadcrumbCallback returns null`() {
    val converter = fixture.getSut()
    val breadcrumb =
      Breadcrumb(Date()).apply {
        message = "test message"
        category = "test.category"
      }
    val hint = Hint()

    val userCallback = SentryOptions.BeforeBreadcrumbCallback { _, _ -> null }
    converter.setUserBeforeBreadcrumbCallback(userCallback)

    val result = converter.execute(breadcrumb, hint)

    assertNull(result)
  }

  @Test
  fun `network data is extracted from hint for http breadcrumbs with user callback`() {
    val converter = fixture.getSut()
    val httpBreadcrumb =
      Breadcrumb(Date()).apply {
        type = "http"
        category = "http"
        data["url"] = "https://example.com"
      }

    val networkData =
      NetworkRequestData(
        "GET",
        200,
        100L,
        500L,
        ReplayNetworkRequestOrResponse(100L, null, mapOf("Content-Type" to "application/json")),
        ReplayNetworkRequestOrResponse(500L, null, mapOf("Content-Type" to "application/json")),
      )
    val hint = Hint()
    hint.set("replay:networkDetails", networkData)

    val userCallback = SentryOptions.BeforeBreadcrumbCallback { b, _ -> b }
    converter.setUserBeforeBreadcrumbCallback(userCallback)

    val result = converter.execute(httpBreadcrumb, hint)

    assertSame(httpBreadcrumb, result)
  }

  @Test
  fun `network data is extracted from hint for http breadcrumbs without user callback`() {
    val converter = fixture.getSut()
    val httpBreadcrumb =
      Breadcrumb(Date()).apply {
        type = "http"
        category = "http"
        data["url"] = "https://example.com"
      }

    val networkData =
      NetworkRequestData(
        "POST",
        201,
        200L,
        400L,
        ReplayNetworkRequestOrResponse(
          200L,
          NetworkBody.fromJsonObject(mapOf("body" to "request")),
          mapOf(),
        ),
        ReplayNetworkRequestOrResponse(
          400L,
          NetworkBody.fromJsonObject(mapOf("body" to "response")),
          mapOf(),
        ),
      )
    val hint = Hint()
    hint.set("replay:networkDetails", networkData)

    converter.setUserBeforeBreadcrumbCallback(null)

    val result = converter.execute(httpBreadcrumb, hint)

    assertSame(httpBreadcrumb, result)
  }

  @Test
  fun `setUserBeforeBreadcrumbCallback updates the callback`() {
    val converter = fixture.getSut()
    val breadcrumb = Breadcrumb(Date()).apply { message = "test" }
    val hint = Hint()

    // First callback modifies the message
    val firstCallback =
      SentryOptions.BeforeBreadcrumbCallback { b, _ ->
        b.message = "modified by first"
        b
      }
    converter.setUserBeforeBreadcrumbCallback(firstCallback)
    var result = converter.execute(breadcrumb, hint)
    assertEquals("modified by first", result?.message)

    // Second callback modifies differently
    val secondCallback =
      SentryOptions.BeforeBreadcrumbCallback { b, _ ->
        b.message = "modified by second"
        b
      }
    converter.setUserBeforeBreadcrumbCallback(secondCallback)

    breadcrumb.message = "test" // Reset
    result = converter.execute(breadcrumb, hint)
    assertEquals("modified by second", result?.message)
  }

  @Test
  fun `user callback receives same breadcrumb and hint objects`() {
    val converter = fixture.getSut()
    val breadcrumb = Breadcrumb(Date()).apply { message = "test" }
    val hint = Hint()

    var capturedBreadcrumb: Breadcrumb? = null
    var capturedHint: Hint? = null

    val capturingCallback =
      SentryOptions.BeforeBreadcrumbCallback { b, h ->
        capturedBreadcrumb = b
        capturedHint = h
        b
      }
    converter.setUserBeforeBreadcrumbCallback(capturingCallback)

    converter.execute(breadcrumb, hint)

    assertSame(breadcrumb, capturedBreadcrumb)
    assertSame(hint, capturedHint)
  }

  @Test
  fun `non-http breadcrumbs are unaltered by network detail data extraction`() {
    val converter = fixture.getSut()
    val navigationBreadcrumb =
      Breadcrumb(Date()).apply {
        type = "navigation"
        category = "navigation"
      }
    val hint = Hint()
    hint.set("replay:networkDetails", NetworkRequestData("GET", 200, null, null, null, null))

    converter.setUserBeforeBreadcrumbCallback(null)

    val result = converter.execute(navigationBreadcrumb, hint)

    assertSame(navigationBreadcrumb, result)
    // Network data extraction only happens for http breadcrumbs
  }
}
