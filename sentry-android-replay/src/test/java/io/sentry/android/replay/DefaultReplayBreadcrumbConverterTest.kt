package io.sentry.android.replay

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SpanDataConvention
import io.sentry.TypeCheckHint.SENTRY_REPLAY_NETWORK_DETAILS
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebSpanEvent
import io.sentry.util.network.NetworkBody
import io.sentry.util.network.NetworkRequestData
import io.sentry.util.network.ReplayNetworkRequestOrResponse
import java.util.Date
import junit.framework.TestCase.assertEquals
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class DefaultReplayBreadcrumbConverterTest {
  class Fixture {
    fun getSut(options: SentryOptions? = null): DefaultReplayBreadcrumbConverter =
      if (options != null) {
        DefaultReplayBreadcrumbConverter(options)
      } else {
        DefaultReplayBreadcrumbConverter()
      }
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
  fun `ReplayBeforeBreadcrumb does not modify breadcrumb__no user-provided BeforeBreadcrumbCallback`() {
    // Create options with no beforeBreadcrumb callback
    val options = SentryOptions.empty()
    options.beforeBreadcrumb = null
    DefaultReplayBreadcrumbConverter(options)

    val breadcrumb =
      Breadcrumb(Date()).apply {
        message = "test message"
        category = "test.category"
      }
    val hint = Hint()

    val result = options.beforeBreadcrumb?.execute(breadcrumb, hint)

    assertSame(breadcrumb, result)
  }

  @Test
  fun `ReplayBeforeBreadcrumb delegates to user-provided BeforeBreadcrumbCallback`() {
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

    // Set up options with a user callback that returns modified breadcrumb
    val userBeforeBreadcrumbCallback =
      SentryOptions.BeforeBreadcrumbCallback { _, _ -> userModifiedBreadcrumb }
    val options = SentryOptions.empty()
    options.beforeBreadcrumb = userBeforeBreadcrumbCallback

    DefaultReplayBreadcrumbConverter(options)

    // user-provided SentryOptions beforeBreadcrumb is replaced.
    assertNotSame(userBeforeBreadcrumbCallback, options.beforeBreadcrumb)

    // SentryOptions#beforeBreadcrumb still respects user-provided beforeBreadcrumb
    val result = options.beforeBreadcrumb?.execute(originalBreadcrumb, Hint())
    assertSame(userModifiedBreadcrumb, result)
  }

  @Test
  fun `ReplayBeforeBreadcrumb handles user-provided BeforeBreadcrumbCallback returning null`() {
    val breadcrumb =
      Breadcrumb(Date()).apply {
        message = "test message"
        category = "test.category"
      }

    val options = SentryOptions.empty()
    val userCallback = SentryOptions.BeforeBreadcrumbCallback { _, _ -> null }
    options.beforeBreadcrumb = userCallback
    fixture.getSut(options)

    // user-provided SentryOptions beforeBreadcrumb is replaced.
    assertNotSame(userCallback, options.beforeBreadcrumb)

    val result = options.beforeBreadcrumb?.execute(breadcrumb, Hint())
    assertNull(result)
  }

  @Test
  fun `converts network details data__with user-provided BeforeBreadcrumbCallback`() {
    val options = SentryOptions.empty()
    val userCallback = SentryOptions.BeforeBreadcrumbCallback { b, _ -> b }
    options.beforeBreadcrumb = userCallback
    val converter = fixture.getSut(options)

    val httpBreadcrumb =
      Breadcrumb(Date(123L)).apply {
        type = "http"
        category = "http"
        data["url"] = "https://example.com"
        data[SpanDataConvention.HTTP_START_TIMESTAMP] = 1000L
        data[SpanDataConvention.HTTP_END_TIMESTAMP] = 2000L
      }

    val fakeOkHttpNetworkDetails = NetworkRequestData("POST")
    fakeOkHttpNetworkDetails.setRequestDetails(
      ReplayNetworkRequestOrResponse(
        100L,
        NetworkBody.fromString("request body content"),
        mapOf("Content-Type" to "application/json"),
      )
    )
    fakeOkHttpNetworkDetails.setResponseDetails(
      200,
      ReplayNetworkRequestOrResponse(
        500L,
        NetworkBody.fromJsonObject(mapOf("status" to "success", "message" to "OK")),
        mapOf("Content-Type" to "text/plain"),
      ),
    )
    val hintWithFakeOKHttpNetworkDetails = Hint()
    hintWithFakeOKHttpNetworkDetails.set(SENTRY_REPLAY_NETWORK_DETAILS, fakeOkHttpNetworkDetails)

    options.beforeBreadcrumb?.execute(httpBreadcrumb, hintWithFakeOKHttpNetworkDetails)

    // Verify NetworkDetails is properly extracted
    val rrwebEvent = converter.convert(httpBreadcrumb)
    check(rrwebEvent is RRWebSpanEvent)

    // Meta data
    assertEquals("POST", rrwebEvent.data!!["method"])
    assertEquals(200, rrwebEvent.data!!["statusCode"])
    assertEquals(100L, rrwebEvent.data!!["requestBodySize"])
    assertEquals(500L, rrwebEvent.data!!["responseBodySize"])

    // Request data
    val requestData = rrwebEvent.data!!["request"] as? Map<*, *>
    assertNotNull(requestData)
    assertEquals(100L, requestData["size"])
    assertEquals("request body content", requestData["body"])
    assertEquals(mapOf("Content-Type" to "application/json"), requestData["headers"])

    // Response data
    val responseData = rrwebEvent.data!!["response"] as? Map<*, *>
    assertNotNull(responseData)
    assertEquals(500L, responseData["size"])
    assertEquals(mapOf("status" to "success", "message" to "OK"), responseData["body"])
    assertEquals(mapOf("Content-Type" to "text/plain"), responseData["headers"])
  }

  @Test
  fun `converts network details data__no user-provided BeforeBreadcrumbCallback`() {
    val options = SentryOptions.empty()
    val userCallback = null
    options.beforeBreadcrumb = userCallback
    val converter = fixture.getSut(options)

    val httpBreadcrumb =
      Breadcrumb(Date(123L)).apply {
        type = "http"
        category = "http"
        data["url"] = "https://example.com"
        data[SpanDataConvention.HTTP_START_TIMESTAMP] = 1000L
        data[SpanDataConvention.HTTP_END_TIMESTAMP] = 2000L
      }

    val fakeOkHttpNetworkDetails = NetworkRequestData("POST")
    fakeOkHttpNetworkDetails.setRequestDetails(
      ReplayNetworkRequestOrResponse(
        150L,
        NetworkBody.fromJsonArray(listOf("item1", "item2", "item3")),
        mapOf("Content-Type" to "application/json"),
      )
    )
    fakeOkHttpNetworkDetails.setResponseDetails(
      404,
      ReplayNetworkRequestOrResponse(
        550L,
        NetworkBody.fromJsonObject(mapOf("status" to "success", "message" to "OK")),
        mapOf("Content-Type" to "text/plain"),
      ),
    )
    val hintWithFakeOKHttpNetworkDetails = Hint()
    hintWithFakeOKHttpNetworkDetails.set(SENTRY_REPLAY_NETWORK_DETAILS, fakeOkHttpNetworkDetails)

    options.beforeBreadcrumb?.execute(httpBreadcrumb, hintWithFakeOKHttpNetworkDetails)

    // Verify NetworkDetails is properly extracted
    val rrwebEvent = converter.convert(httpBreadcrumb)
    check(rrwebEvent is RRWebSpanEvent)

    // Meta data
    assertEquals("POST", rrwebEvent.data!!["method"])
    assertEquals(404, rrwebEvent.data!!["statusCode"])
    assertEquals(150L, rrwebEvent.data!!["requestBodySize"])
    assertEquals(550L, rrwebEvent.data!!["responseBodySize"])

    // Request data
    val requestData = rrwebEvent.data!!["request"] as? Map<*, *>
    assertNotNull(requestData)
    assertEquals(150L, requestData["size"])
    assertEquals(listOf("item1", "item2", "item3"), requestData["body"])
    assertEquals(mapOf("Content-Type" to "application/json"), requestData["headers"])

    // Response data
    val responseData = rrwebEvent.data!!["response"] as? Map<*, *>
    assertNotNull(responseData)
    assertEquals(550L, responseData["size"])
    assertEquals(mapOf("status" to "success", "message" to "OK"), responseData["body"])
    assertEquals(mapOf("Content-Type" to "text/plain"), responseData["headers"])
  }

  @Test
  fun `does not convert network details data for non-http breadcrumbs`() {
    val navigationBreadcrumb =
      Breadcrumb(Date()).apply {
        type = "navigation"
        category = "navigation"
        data["to"] = "/home"
      }
    val hint = Hint()
    val networkRequestData = NetworkRequestData("GET")
    networkRequestData.setRequestDetails(
      ReplayNetworkRequestOrResponse(
        100L,
        NetworkBody.fromString("request body content"),
        mapOf("Content-Type" to "application/json"),
      )
    )
    networkRequestData.setResponseDetails(
      200,
      ReplayNetworkRequestOrResponse(
        100L,
        NetworkBody.fromString("respnse body content"),
        mapOf("Content-Type" to "application/json"),
      ),
    )
    hint.set(SENTRY_REPLAY_NETWORK_DETAILS, networkRequestData)

    val options = SentryOptions.empty()
    options.beforeBreadcrumb = null
    val converter = fixture.getSut(options)

    assertSame(navigationBreadcrumb, options.beforeBreadcrumb?.execute(navigationBreadcrumb, hint))

    // Verify converter also doesn't include network details for non-http breadcrumbs
    val rrwebEvent = converter.convert(navigationBreadcrumb)
    check(rrwebEvent is RRWebBreadcrumbEvent)
    assertEquals("navigation", rrwebEvent.category)
    assertEquals("/home", rrwebEvent.data!!["to"])

    // Verify no network-related data is present
    assertNull(rrwebEvent.data!!["method"])
    assertNull(rrwebEvent.data!!["statusCode"])
    assertNull(rrwebEvent.data!!["requestBodySize"])
    assertNull(rrwebEvent.data!!["responseBodySize"])
    assertNull(rrwebEvent.data!!["request"])
    assertNull(rrwebEvent.data!!["response"])
  }
}
