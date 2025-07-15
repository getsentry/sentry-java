package io.sentry.ktorClient

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.plugins.plugin
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
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.exception.SentryHttpClientException
import io.sentry.mockServerRequestTimeoutMillis
import io.sentry.okhttp.SentryOkHttpInterceptor
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import okhttp3.OkHttpClient
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
      httpClientEngine: HttpClientEngine = Java.create(),
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

      whenever(scopes.forkedCurrentScope(any())).thenReturn(scopes)

      server.enqueue(
        MockResponse()
          .setBody(responseBody)
          .addHeader("myResponseHeader", "myValue")
          .setSocketPolicy(socketPolicy)
          .setResponseCode(httpStatusCode)
      )

      return HttpClient(httpClientEngine) {
        install(SentryKtorClientPlugin) {
          this.scopes = this@Fixture.scopes
          this.captureFailedRequests = captureFailedRequests
          this.failedRequestTargets = failedRequestTargets
          this.failedRequestStatusCodes = failedRequestStatusCodes
          this.forceScopes = true
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
  fun `captures an event with request and response contexts if captureFailedRequests is enabled and status code is within the range`():
    Unit = runBlocking {
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
        failedRequestStatusCodes = listOf(HttpStatusCodeRange(400, 499)),
      )
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `does not capture failed requests outside custom status code ranges`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500,
        failedRequestStatusCodes = listOf(HttpStatusCodeRange(400, 499)),
      )
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
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
  fun `creates a span around the request`(): Unit = runBlocking {
    val sut = fixture.getSut()
    val url = fixture.server.url("/hello").toString()
    sut.get(url)

    assertEquals(1, fixture.sentryTracer.children.size)
    val httpClientSpan = fixture.sentryTracer.children.first()
    assertEquals("http.client", httpClientSpan.operation)
    assertEquals("GET $url", httpClientSpan.description)
    assertEquals("auto.http.ktor-client", httpClientSpan.spanContext.origin)
    assertEquals(SpanStatus.OK, httpClientSpan.status)
    assertTrue(httpClientSpan.isFinished)
  }

  @Test
  fun `finishes span setting throwable and status when request throws`(): Unit = runBlocking {
    val sut = fixture.getSut(socketPolicy = SocketPolicy.DISCONNECT_DURING_REQUEST_BODY)

    var exception: Exception? = null
    try {
      sut.post(fixture.server.url("/hello?myQuery=myValue#myFragment").toString()) {
        contentType(ContentType.Text.Plain)
        setBody("hello hello")
      }
    } catch (e: Exception) {
      exception = e
    }

    val httpClientSpan = fixture.sentryTracer.children.first()
    assertTrue(httpClientSpan.isFinished)
    assertEquals(SpanStatus.INTERNAL_ERROR, httpClientSpan.status)
    assertEquals(
      httpClientSpan.throwable.toString(),
      exception.toString(),
    ) // stack trace will differ
  }

  @Test
  fun `when there is an active span and server is listed in tracing origins, adds sentry trace headers to the request`():
    Unit = runBlocking {
    val sut = fixture.getSut()
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span and tracing origins contains default regex, adds sentry trace headers to the request`():
    Unit = runBlocking {
    val sut = fixture.getSut(keepDefaultTracePropagationTargets = true)
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span and server is not listed in propagation targets, does not add sentry trace headers to the request`():
    Unit = runBlocking {
    val sut =
      fixture.getSut(
        includeMockServerInTracePropagationTargets = false,
        keepDefaultTracePropagationTargets = false,
      )
    fixture.options.setTracePropagationTargets(emptyList())
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is no active span, adds sentry trace header to the request from scope`(): Unit =
    runBlocking {
      val sut = fixture.getSut(isSpanActive = false)
      sut.get(fixture.server.url("/hello").toString())

      val recordedRequest =
        fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
      assertNotNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
      assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

  @Test
  fun `does not add sentry-trace header when span origin is ignored`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        isSpanActive = false,
        optionsConfiguration = { options ->
          options.setIgnoredSpanOrigins(listOf("auto.http.ktor-client"))
        }
      )
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is no active span and host is not allowed, does not add sentry trace header to the request`():
    Unit = runBlocking {
    val sut = fixture.getSut(isSpanActive = false)
    fixture.options.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span, existing baggage headers are merged with sentry baggage into single header`():
    Unit = runBlocking {
    val sut = fixture.getSut()
    sut.get(fixture.server.url("/hello").toString()) {
      headers["baggage"] = "thirdPartyBaggage=someValue"
      headers.append(
        "baggage",
        "secondThirdPartyBaggage=secondValue; property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue",
      )
    }

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
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

  @Test
  fun `is disabled when using OkHttp client with Sentry interceptor added to builder`() {
    val okHttpClient = OkHttpClient.Builder()
      .addInterceptor(SentryOkHttpInterceptor())
      .build()
    val engine = OkHttpEngine(OkHttpConfig().apply { 
      preconfigured = okHttpClient
    })
    
    val client = fixture.getSut(httpClientEngine = engine)
    val plugin = client.plugin(SentryKtorClientPlugin)
  }

  @Test
  fun `is disabled when using preconfigured OkHttp client with Sentry interceptor`() {
    val engine = OkHttpEngine(OkHttpConfig())
    val client = fixture.getSut(httpClientEngine = engine)
  }
}
