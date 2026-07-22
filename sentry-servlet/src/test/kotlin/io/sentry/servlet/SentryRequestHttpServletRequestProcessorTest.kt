package io.sentry.servlet

import io.sentry.Hint
import io.sentry.KeyValueCollectionBehavior
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

class SentryRequestHttpServletRequestProcessorTest {
  @Test
  fun `attaches basic information from HTTP request to SentryEvent`() {
    val request =
      MockMvcRequestBuilders.get(URI.create("http://example.com?param1=xyz"))
        .header("some-header", "some-header value")
        .accept("application/json")
        .buildRequest(MockServletContext())
    val eventProcessor = SentryRequestHttpServletRequestProcessor(request, SentryOptions())
    val event = SentryEvent()

    eventProcessor.process(event, Hint())

    assertNotNull(event.request)
    val eventRequest = event.request!!
    assertEquals("GET", eventRequest.method)
    assertEquals(
      mapOf("some-header" to "some-header value", "Accept" to "application/json"),
      eventRequest.headers,
    )
    assertEquals("http://example.com", eventRequest.url)
    assertEquals("param1=xyz", eventRequest.queryString)
  }

  @Test
  fun `data collection filters query parameters`() {
    val request =
      MockMvcRequestBuilders.get(URI.create("http://example.com?name=value&token=secret"))
        .buildRequest(MockServletContext())
    val options = SentryOptions().also { it.dataCollection.setUserInfo(false) }
    val event = SentryEvent()

    SentryRequestHttpServletRequestProcessor(request, options).process(event, Hint())

    assertEquals("name=value&token=[Filtered]", event.request!!.queryString)
  }

  @Test
  fun `data collection can disable query parameters`() {
    val request =
      MockMvcRequestBuilders.get(URI.create("http://example.com?name=value"))
        .buildRequest(MockServletContext())
    val options =
      SentryOptions().also { it.dataCollection.queryParams = KeyValueCollectionBehavior.off() }
    val event = SentryEvent()

    SentryRequestHttpServletRequestProcessor(request, options).process(event, Hint())

    assertNull(event.request!!.queryString)
  }

  @Test
  fun `attaches header with multiple values`() {
    val request =
      MockMvcRequestBuilders.get(URI.create("http://example.com?param1=xyz"))
        .header("another-header", "another value")
        .header("another-header", "another value2")
        .buildRequest(MockServletContext())
    val eventProcessor = SentryRequestHttpServletRequestProcessor(request, SentryOptions())
    val event = SentryEvent()

    eventProcessor.process(event, Hint())

    assertNotNull(event.request) {
      assertEquals(mapOf("another-header" to "another value,another value2"), it.headers)
    }
  }

  @Test
  fun `does not attach cookies`() {
    val request =
      MockMvcRequestBuilders.get(URI.create("http://example.com?param1=xyz"))
        .header("Cookie", "name=value")
        .buildRequest(MockServletContext())
    val sentryOptions = SentryOptions()
    sentryOptions.isSendDefaultPii = false
    val eventProcessor = SentryRequestHttpServletRequestProcessor(request, sentryOptions)
    val event = SentryEvent()

    eventProcessor.process(event, Hint())

    assertNotNull(event.request) { assertNull(it.cookies) }
  }

  @Test
  fun `data collection filters request headers`() {
    val request =
      MockMvcRequestBuilders.get(URI.create("http://example.com"))
        .header("content-type", "application/json")
        .header("authorization", "Bearer token")
        .header("x-customer", "customer value")
        .buildRequest(MockServletContext())
    val options =
      SentryOptions().also {
        it.dataCollection.httpHeaders.request = KeyValueCollectionBehavior.denyList("customer")
      }
    val event = SentryEvent()

    SentryRequestHttpServletRequestProcessor(request, options).process(event, Hint())

    assertEquals(
      mapOf(
        "Content-Type" to "application/json",
        "authorization" to "[Filtered]",
        "x-customer" to "[Filtered]",
      ),
      event.request!!.headers,
    )
  }

  @Test
  fun `data collection can disable request headers`() {
    val request =
      MockMvcRequestBuilders.get(URI.create("http://example.com"))
        .header("content-type", "application/json")
        .buildRequest(MockServletContext())
    val options =
      SentryOptions().also {
        it.dataCollection.httpHeaders.request = KeyValueCollectionBehavior.off()
      }
    val event = SentryEvent()

    SentryRequestHttpServletRequestProcessor(request, options).process(event, Hint())

    assertEquals(emptyMap(), event.request!!.headers)
  }

  @Test
  fun `does not attach sensitive headers`() {
    val request =
      MockMvcRequestBuilders.get(URI.create("http://example.com?param1=xyz"))
        .header("some-header", "some-header value")
        .header("X-FORWARDED-FOR", "192.168.0.1")
        .header("authorization", "Token")
        .header("Authorization", "Token")
        .header("Cookie", "some cookies")
        .buildRequest(MockServletContext())
    val sentryOptions = SentryOptions()
    sentryOptions.isSendDefaultPii = false
    val eventProcessor = SentryRequestHttpServletRequestProcessor(request, sentryOptions)
    val event = SentryEvent()

    eventProcessor.process(event, Hint())

    assertNotNull(event.request) { req ->
      assertNotNull(req.headers) {
        assertFalse(it.containsKey("X-FORWARDED-FOR"))
        assertFalse(it.containsKey("Authorization"))
        assertFalse(it.containsKey("authorization"))
        assertFalse(it.containsKey("Cookies"))
        assertTrue(it.containsKey("some-header"))
      }
    }
  }
}
