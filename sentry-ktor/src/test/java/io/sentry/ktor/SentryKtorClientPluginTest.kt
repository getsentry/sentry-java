package io.sentry.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HttpStatusCodeRange
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SpanDataConvention
import io.sentry.exception.SentryHttpClientException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    var options: SentryOptions? = null
    var scope: IScope? = null

    @SuppressWarnings("LongParameterList")
    fun getSut(
      httpStatusCode: Int = 201,
      responseBody: String = "success",
      socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
      captureFailedRequests: Boolean = false,
      failedRequestTargets: List<String> = listOf(".*"),
      failedRequestStatusCodes: List<HttpStatusCodeRange> =
        listOf(
          HttpStatusCodeRange(HttpStatusCodeRange.DEFAULT_MIN, HttpStatusCodeRange.DEFAULT_MAX)
        ),
      sendDefaultPii: Boolean = false,
      optionsConfiguration: ((SentryOptions) -> Unit)? = null,
    ): HttpClient {
      options =
        SentryOptions().also {
          it.dsn = "https://key@sentry.io/proj"
          it.isSendDefaultPii = sendDefaultPii
          optionsConfiguration?.invoke(it)
        }
      scope = Scope(options!!)
      whenever(scopes.options).thenReturn(options)
      doAnswer { (it.arguments[0] as ScopeCallback).run(scope!!) }
        .whenever(scopes)
        .configureScope(any())

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
}
