package io.sentry.android.replay

import io.sentry.Breadcrumb
import io.sentry.SentryLevel
import io.sentry.SpanDataConvention
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebSpanEvent
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertNull

class DefaultReplayBreadcrumbConverterTest {
    class Fixture {
        fun getSut(): DefaultReplayBreadcrumbConverter {
            return DefaultReplayBreadcrumbConverter()
        }
    }

    private val fixture = Fixture()

    @Test
    fun `returns null when no category`() {
        val converter = fixture.getSut()

        val breadcrumb = Breadcrumb(Date(123L)).apply {
            message = "message"
        }

        val rrwebEvent = converter.convert(breadcrumb)

        assertNull(rrwebEvent)
    }

    @Test
    fun `convert RRWebSpanEvent`() {
        val converter = fixture.getSut()

        val breadcrumb = Breadcrumb(Date(123L)).apply {
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
    fun `returns null if not eligible for RRWebSpanEvent`() {
        val converter = fixture.getSut()

        val breadcrumb = Breadcrumb(Date(123L)).apply {
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

        val breadcrumb = Breadcrumb(Date(123L)).apply {
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

        val breadcrumb = Breadcrumb(Date(123L)).apply {
            category = "device.orientation"
            type = "navigation"
            data["position"] = "landscape"
        }

        val rrwebEvent = converter.convert(breadcrumb)

        check(rrwebEvent is RRWebBreadcrumbEvent)
        assertEquals("device.orientation", rrwebEvent.category)
        assertEquals("landscape", rrwebEvent.data!!["position"])
        assertEquals(123L, rrwebEvent.timestamp)
        assertEquals(0.123, rrwebEvent.breadcrumbTimestamp)
        assertEquals("default", rrwebEvent.breadcrumbType)
    }

    @Test
    fun `returns null if no position for orientation breadcrumbs`() {
        val converter = fixture.getSut()

        val breadcrumb = Breadcrumb(Date(123L)).apply {
            category = "device.orientation"
            type = "navigation"
        }

        val rrwebEvent = converter.convert(breadcrumb)

        assertNull(rrwebEvent)
    }

    @Test
    fun `converts navigation breadcrumbs`() {
        val converter = fixture.getSut()

        val breadcrumb = Breadcrumb(Date(123L)).apply {
            category = "navigation"
            type = "navigation"
            data["state"] = "resumed"
            data["screen"] = "io.sentry.MainActivity"
        }

        val rrwebEvent = converter.convert(breadcrumb)

        check(rrwebEvent is RRWebBreadcrumbEvent)
        assertEquals("navigation", rrwebEvent.category)
        assertEquals("MainActivity", rrwebEvent.data!!["to"])
        assertEquals(123L, rrwebEvent.timestamp)
        assertEquals(0.123, rrwebEvent.breadcrumbTimestamp)
        assertEquals("default", rrwebEvent.breadcrumbType)
    }

    @Test
    fun `converts navigation breadcrumbs with destination`() {
        val converter = fixture.getSut()

        val breadcrumb = Breadcrumb(Date(123L)).apply {
            category = "navigation"
            type = "navigation"
            data["to"] = "/github"
        }

        val rrwebEvent = converter.convert(breadcrumb)

        check(rrwebEvent is RRWebBreadcrumbEvent)
        assertEquals("navigation", rrwebEvent.category)
        assertEquals("/github", rrwebEvent.data!!["to"])
        assertEquals(123L, rrwebEvent.timestamp)
        assertEquals(0.123, rrwebEvent.breadcrumbTimestamp)
        assertEquals("default", rrwebEvent.breadcrumbType)
    }

//    @Test
//    fun `test convert with navigation and app lifecycle`() {
//        val breadcrumb = Breadcrumb().apply {
//            message = "message"
//            category = "navigation"
//            type = "navigation"
//            level = SentryLevel.ERROR
//            data["state"] = "resumed"
//            data["screen"] = "screen"
//        }
//
//        val result = DefaultReplayBreadcrumbConverter().convert(breadcrumb)
//
//        assertTrue(result is RRWebBreadcrumbEvent)
//        assertEquals("navigation", result.category)
//        assertEquals("navigation", result.type)
//        assertEquals(SentryLevel.ERROR, result.level)
//        assertEquals("resumed", result.data["state"])
//        assertEquals("screen", result.data["screen"])
//    }
//
//    @Test
//    fun `test convert with navigation and device orientation`() {
//        val breadcrumb = Breadcrumb().apply {
//            message = "message"
//            category = "navigation"
//            type = "navigation"
//            level = SentryLevel.ERROR
//            data["position"] = "landscape"
//        }
//
//        val result = DefaultReplayBreadcrumbConverter().convert(breadcrumb)
//
//        assertTrue(result is RRWebBreadcrumbEvent)
//        assertEquals("navigation", result.category)
//        assertEquals("navigation", result.type)
//        assertEquals(SentryLevel.ERROR, result.level)
//        assertEquals("landscape", result.data["position"])
//    }
}
