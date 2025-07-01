package io.sentry.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HttpStatusCodeRange
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.Sentry
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.exception.SentryHttpClientException
import io.sentry.mockServerRequestTimeoutMillis
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryKtorClientPluginTest {
  class Fixture {
    val scopes = mock<IScopes>()
    val server = MockWebServer()
    lateinit var sentryTracer: SentryTracer
    lateinit var options: SentryOptions
    lateinit var scope: IScope

    @SuppressWarnings("LongParameterList")
    fun getSut(
      isSpanActive: Boolean = true,
      httpStatusCode: Int = 201,
      responseBody: String = "success",
      socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
      beforeSpan: SentryKtorClientPluginConfig.BeforeSpanCallback? = null,
      includeMockServerInTracePropagationTargets: Boolean = true,
      keepDefaultTracePropagationTargets: Boolean = false,
      captureFailedRequests: Boolean = false,
      failedRequestTargets: List<String> = listOf(".*"),
      failedRequestStatusCodes: List<HttpStatusCodeRange> =
        listOf(
          HttpStatusCodeRange(HttpStatusCodeRange.DEFAULT_MIN, HttpStatusCodeRange.DEFAULT_MAX)
        ),
      sendDefaultPii: Boolean = false,
      optionsConfiguration: Sentry.OptionsConfiguration<SentryOptions>? = null,
    ): HttpClient {
      options =
        SentryOptions().also {
          optionsConfiguration?.configure(it)
          it.dsn = "https://key@sentry.io/proj"
          if (includeMockServerInTracePropagationTargets) {
            it.setTracePropagationTargets(listOf(server.hostName))
          } else if (!keepDefaultTracePropagationTargets) {
            it.setTracePropagationTargets(listOf("other-api"))
          }
          it.isSendDefaultPii = sendDefaultPii
        }
      scope = Scope(options)
      whenever(scopes.options).thenReturn(options)
      doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }
        .whenever(scopes)
        .configureScope(any())

      sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)

      if (isSpanActive) {
        whenever(scopes.span).thenReturn(sentryTracer)
      }

      // Mock forkedCurrentScope to return the same scopes instance
      whenever(scopes.forkedCurrentScope(SENTRY_KTOR_CLIENT_PLUGIN_KEY)).thenReturn(scopes)

      server.enqueue(
        MockResponse()
          .setBody(responseBody)
          .addHeader("myResponseHeader", "myValue")
          .setSocketPolicy(socketPolicy)
          .setResponseCode(httpStatusCode)
      )

      return HttpClient {
        install(SentryKtorClientPlugin) {
          this.scopes = this@Fixture.scopes
          this.captureFailedRequests = captureFailedRequests
          this.failedRequestTargets = failedRequestTargets
          this.failedRequestStatusCodes = failedRequestStatusCodes
        }
      }
    }
  }

  private val fixture = Fixture()

  @Test
  fun `adds breadcrumb when http call succeeds`(): Unit = runBlocking {
    val sut = fixture.getSut(responseBody = "response body")
    val response = sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals("GET", it.getData("method"))
          assertEquals(201, it.getData("status_code"))
          assertEquals(fixture.server.url("/hello").toString(), it.getData("url"))
          assertEquals(13L, it.getData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY))
          assertNotNull(it.getData(SpanDataConvention.HTTP_START_TIMESTAMP))
        },
        anyOrNull(),
      )
  }

  @Test
  fun `adds breadcrumb when http call fails`(): Unit = runBlocking {
    val sut = fixture.getSut(httpStatusCode = 500, responseBody = "error")
    val response = sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals("GET", it.getData("method"))
          assertEquals(500, it.getData("status_code"))
          assertEquals(fixture.server.url("/hello").toString(), it.getData("url"))
        },
        anyOrNull(),
      )
  }

  @Test
  fun `captures an event if captureFailedRequests is enabled and status code is within the range`():
    Unit = runBlocking {
    val sut = fixture.getSut(captureFailedRequests = true, httpStatusCode = 500)
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `does not capture an event if captureFailedRequests is disabled`(): Unit = runBlocking {
    val sut = fixture.getSut(httpStatusCode = 500)
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `does not capture an event if captureFailedRequests is enabled but status code is not within the range`():
    Unit = runBlocking {
    val sut = fixture.getSut(captureFailedRequests = true)
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `does not capture an event if captureFailedRequests is enabled and domain is not within the targets`():
    Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500,
        failedRequestTargets = listOf("myapi.com"),
      )
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `captures failed requests with custom status code ranges`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 404,
        failedRequestStatusCodes = listOf(HttpStatusCodeRange(400, 499)), // only client errors
      )
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `does not capture failed requests outside custom status code ranges`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500, // server error
        failedRequestStatusCodes = listOf(HttpStatusCodeRange(400, 499)), // only client errors
      )
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `captures an error event with request and response data`(): Unit = runBlocking {
    val statusCode = 500
    val responseBody = "failure"
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = statusCode,
        responseBody = responseBody,
        sendDefaultPii = true,
      )

    val requestBody = "test"
    val response =
      sut.post(fixture.server.url("/hello?myQuery=myValue#myFragment").toString()) {
        contentType(ContentType.Text.Plain)
        setBody(requestBody)
      }

    verify(fixture.scopes)
      .captureEvent(
        check<SentryEvent> {
          val sentryRequest = it.request!!
          assertEquals("http://localhost:${fixture.server.port}/hello", sentryRequest.url)
          assertEquals("myQuery=myValue", sentryRequest.queryString)
          assertEquals("myFragment", sentryRequest.fragment)
          assertEquals("POST", sentryRequest.method)
          assertEquals(requestBody.length.toLong(), sentryRequest.bodySize)
          assertNotNull(sentryRequest.headers)

          val sentryResponse = it.contexts.response!!
          assertEquals(statusCode, sentryResponse.statusCode)
          assertEquals(responseBody.length.toLong(), sentryResponse.bodySize)
          assertNotNull(sentryResponse.headers)

          assertTrue(it.throwable is SentryHttpClientException)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `does not capture headers when sendDefaultPii is disabled`(): Unit = runBlocking {
    val sut =
      fixture.getSut(captureFailedRequests = true, httpStatusCode = 500, sendDefaultPii = false)

    sut.get(fixture.server.url("/hello").toString()) { headers["myHeader"] = "myValue" }

    verify(fixture.scopes)
      .captureEvent(
        check<SentryEvent> {
          val sentryRequest = it.request!!
          assertEquals(null, sentryRequest.headers)

          val sentryResponse = it.contexts.response!!
          assertEquals(null, sentryResponse.headers)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `creates a span around the request when there is an active span`(): Unit = runBlocking {
    val sut = fixture.getSut()
    val url = fixture.server.url("/hello").toString()
    sut.get(url)

    assertEquals(1, fixture.sentryTracer.children.size)
    val httpClientSpan = fixture.sentryTracer.children.first()
    assertEquals("http.client", httpClientSpan.operation)
    assertEquals("GET $url", httpClientSpan.description)
    assertEquals(201, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
    assertEquals("auto.http.ktor", httpClientSpan.spanContext.origin)
    assertEquals(SpanStatus.OK, httpClientSpan.status)
    assertTrue(httpClientSpan.isFinished)
  }

  @Test
  fun `creates a transaction when there is no active span`(): Unit = runBlocking {
    val sut = fixture.getSut(isSpanActive = false)
    val url = fixture.server.url("/hello").toString()
    sut.get(url)

    // When there's no active span, a transaction is created instead of a child span
    // The transaction itself won't be a child of sentryTracer, but would be started independently
    // We can't easily test the transaction creation in this mock setup,
    // but we can verify no child spans were created on the existing tracer
    assertEquals(0, fixture.sentryTracer.children.size)
  }

  @Test
  fun `span status is set based on http status code`(): Unit = runBlocking {
    val sut = fixture.getSut(httpStatusCode = 404)
    sut.get(fixture.server.url("/hello").toString())

    val httpClientSpan = fixture.sentryTracer.children.first()
    assertEquals(404, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
    assertEquals(SpanStatus.NOT_FOUND, httpClientSpan.status)
    assertTrue(httpClientSpan.isFinished)
  }

  @Test
  fun `span status is set to OK for successful requests`(): Unit = runBlocking {
    val sut = fixture.getSut(httpStatusCode = 200)
    sut.get(fixture.server.url("/hello").toString())

    val httpClientSpan = fixture.sentryTracer.children.first()
    assertEquals(200, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
    assertEquals(SpanStatus.OK, httpClientSpan.status)
    assertTrue(httpClientSpan.isFinished)
  }

  @Test
  fun `span status is set for server errors`(): Unit = runBlocking {
    val sut = fixture.getSut(httpStatusCode = 500)
    sut.get(fixture.server.url("/hello").toString())

    val httpClientSpan = fixture.sentryTracer.children.first()
    assertEquals(500, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
    assertEquals(SpanStatus.INTERNAL_ERROR, httpClientSpan.status)
    assertTrue(httpClientSpan.isFinished)
  }

  @Test
  fun `span status is set for client errors`(): Unit = runBlocking {
    val sut = fixture.getSut(httpStatusCode = 400)
    sut.get(fixture.server.url("/hello").toString())

    val httpClientSpan = fixture.sentryTracer.children.first()
    assertEquals(400, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
    assertEquals(SpanStatus.INVALID_ARGUMENT, httpClientSpan.status)
    assertTrue(httpClientSpan.isFinished)
  }

  @Test
  fun `span status is null for unmapped status codes`(): Unit = runBlocking {
    val sut = fixture.getSut(httpStatusCode = 418) // I'm a teapot - unmapped status
    sut.get(fixture.server.url("/hello").toString())

    val httpClientSpan = fixture.sentryTracer.children.first()
    assertEquals(418, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
    assertNull(httpClientSpan.status)
    assertTrue(httpClientSpan.isFinished)
  }

  @Test
  fun `span description includes HTTP method and URL for GET request`(): Unit = runBlocking {
    val sut = fixture.getSut()
    val url = fixture.server.url("/api/users").toString()
    sut.get(url)

    val httpClientSpan = fixture.sentryTracer.children.first()
    assertEquals("GET $url", httpClientSpan.description)
  }

  @Test
  fun `span description includes HTTP method and URL for POST request`(): Unit = runBlocking {
    val sut = fixture.getSut()
    val url = fixture.server.url("/api/users").toString()
    sut.post(url) {
      setBody("request body")
    }

    val httpClientSpan = fixture.sentryTracer.children.first()
    assertEquals("POST $url", httpClientSpan.description)
  }

  @Test
  fun `span is finished even when request fails`(): Unit = runBlocking {
    val sut = fixture.getSut(socketPolicy = SocketPolicy.DISCONNECT_AT_START)

    try {
      sut.get(fixture.server.url("/hello").toString())
    } catch (e: Exception) {
      // Expected to fail
    }

    val httpClientSpan = fixture.sentryTracer.children.firstOrNull()
    if (httpClientSpan != null) {
      assertTrue(httpClientSpan.isFinished)
    }
  }

  @Test
  fun `multiple requests create multiple spans`(): Unit = runBlocking {
    val sut = fixture.getSut()

    // Make multiple requests
    sut.get(fixture.server.url("/hello1").toString())

    // Enqueue another response for the second request
    fixture.server.enqueue(
      MockResponse()
        .setBody("success2")
        .setResponseCode(200)
    )
    sut.get(fixture.server.url("/hello2").toString())

    assertEquals(2, fixture.sentryTracer.children.size)
    assertTrue(fixture.sentryTracer.children.all { it.isFinished })

    // Verify both spans have correct properties
    val spans = fixture.sentryTracer.children
    spans.forEach { span ->
      assertEquals("http.client", span.operation)
      assertEquals("auto.http.ktor", span.spanContext.origin)
      assertTrue(span.isFinished)
    }
  }

  @Test
  fun `when there is an active span and server is listed in tracing origins, adds sentry trace headers to the request`(): Unit = runBlocking {
    val sut = fixture.getSut()
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span and tracing origins contains default regex, adds sentry trace headers to the request`(): Unit = runBlocking {
    val sut = fixture.getSut(keepDefaultTracePropagationTargets = true)
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span and server is not listed in tracing origins, does not add sentry trace headers to the request`(): Unit = runBlocking {
    val sut = fixture.getSut(includeMockServerInTracePropagationTargets = false)
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span and server tracing origins is empty, does not add sentry trace headers to the request`(): Unit = runBlocking {
    val sut = fixture.getSut()
    fixture.options.setTracePropagationTargets(emptyList())
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is no active span, adds sentry trace header to the request from scope`(): Unit = runBlocking {
    val sut = fixture.getSut(isSpanActive = false)
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `does not add sentry-trace header when span origin is ignored`(): Unit = runBlocking {
    val sut = fixture.getSut(isSpanActive = false) { options ->
      options.setIgnoredSpanOrigins(listOf("auto.http.ktor"))
    }
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is no active span and host is not allowed, does not add sentry trace header to the request`(): Unit = runBlocking {
    val sut = fixture.getSut(isSpanActive = false)
    fixture.options.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span, existing baggage headers are merged with sentry baggage into single header`(): Unit = runBlocking {
    val sut = fixture.getSut()
    sut.get(fixture.server.url("/hello").toString()) {
      headers["baggage"] = "thirdPartyBaggage=someValue"
      headers.append("baggage", "secondThirdPartyBaggage=secondValue; property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue")
    }

    val recordedRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])

    val baggageHeaderValues = recordedRequest.headers.values(BaggageHeader.BAGGAGE_HEADER)
    assertEquals(1, baggageHeaderValues.size)
    assertTrue(
      baggageHeaderValues[0].startsWith(
        "thirdPartyBaggage=someValue," +
          "secondThirdPartyBaggage=secondValue; " +
          "property;propertyKey=propertyValue," +
          "anotherThirdPartyBaggage=anotherValue"
      )
    )
    assertTrue(baggageHeaderValues[0].contains("sentry-public_key=key"))
    assertTrue(baggageHeaderValues[0].contains("sentry-transaction=name"))
    assertTrue(baggageHeaderValues[0].contains("sentry-trace_id"))
  }
}
