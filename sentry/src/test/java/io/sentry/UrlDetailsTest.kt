package io.sentry

import io.sentry.protocol.Request
import io.sentry.util.UrlUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class UrlDetailsTest {
  @Test
  fun `does not crash on null span`() {
    val urlDetails = UrlUtils.UrlDetails("https://sentry.io/api", "q=1", "top")
    urlDetails.applyToSpan(null)
  }

  @Test
  fun `applies query and fragment to span`() {
    val urlDetails = UrlUtils.UrlDetails("https://sentry.io/api", "q=1", "top")
    val span = mock<ISpan>()
    urlDetails.applyToSpan(span)

    verify(span).setData(SpanDataConvention.HTTP_QUERY_KEY, "q=1")
    verify(span).setData(SpanDataConvention.HTTP_FRAGMENT_KEY, "top")
  }

  @Test
  fun `applies query to span`() {
    val urlDetails = UrlUtils.UrlDetails("https://sentry.io/api", "q=1", null)
    val span = mock<ISpan>()
    urlDetails.applyToSpan(span)

    verify(span).setData(SpanDataConvention.HTTP_QUERY_KEY, "q=1")
    verifyNoMoreInteractions(span)
  }

  @Test
  fun `applies fragment to span`() {
    val urlDetails = UrlUtils.UrlDetails("https://sentry.io/api", null, "top")
    val span = mock<ISpan>()
    urlDetails.applyToSpan(span)

    verify(span).setData(SpanDataConvention.HTTP_FRAGMENT_KEY, "top")
    verifyNoMoreInteractions(span)
  }

  @Test
  fun `does not crash on null request`() {
    val urlDetails = UrlUtils.UrlDetails("https://sentry.io/api", "q=1", "top")
    urlDetails.applyToRequest(null)
  }

  @Test
  fun `applies details to request`() {
    val urlDetails = UrlUtils.UrlDetails("https://sentry.io/api", "q=1", "top")
    val request = Request()
    urlDetails.applyToRequest(request)

    assertEquals("https://sentry.io/api", request.url)
    assertEquals("q=1", request.queryString)
    assertEquals("top", request.fragment)
  }

  @Test
  fun `applies details without fragment and url to request`() {
    val urlDetails = UrlUtils.UrlDetails("https://sentry.io/api", null, null)
    val request = Request()
    urlDetails.applyToRequest(request)

    assertEquals("https://sentry.io/api", request.url)
    assertNull(request.queryString)
    assertNull(request.fragment)
  }

  @Test
  fun `returns fallback for null URL`() {
    val urlDetails = UrlUtils.UrlDetails(null, null, null)
    assertEquals("unknown", urlDetails.urlOrFallback)
  }
}
