package io.sentry.apollo3

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.exception.ApolloException
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TraceContext
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.apollo3.SentryApollo3HttpInterceptor.BeforeSpanCallback
import io.sentry.mockServerRequestTimeoutMillis
import io.sentry.protocol.SentryTransaction
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryApollo3InterceptorWithVariablesTest {
  class Fixture {
    val server = MockWebServer()
    val scopes = mock<IScopes>()

    @SuppressWarnings("LongParameterList")
    fun getSut(
      httpStatusCode: Int = 200,
      responseBody: String =
        """{
  "data": {
    "launch": {
      "__typename": "Launch",
      "id": "83",
      "site": "CCAFS SLC 40",
      "mission": {
        "__typename": "Mission",
        "name": "Amos-17",
        "missionPatch": "https://images2.imgbox.com/a0/ab/XUoByiuR_o.png"
      }
    }
  }
}""",
      socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
      beforeSpan: BeforeSpanCallback? = null,
    ): ApolloClient {
      whenever(scopes.options)
        .thenReturn(SentryOptions().apply { dsn = "http://key@localhost/proj" })

      server.enqueue(
        MockResponse()
          .setBody(responseBody)
          .setSocketPolicy(socketPolicy)
          .setResponseCode(httpStatusCode)
      )

      return ApolloClient.Builder()
        .serverUrl(server.url("/").toString())
        .sentryTracing(scopes = scopes, beforeSpan = beforeSpan, captureFailedRequests = false)
        .build()
    }
  }

  private val fixture = Fixture()

  @Test
  fun `creates a span around the successful request`() {
    executeQuery()

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it)
          assertEquals(SpanStatus.OK, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `creates a span around the failed request`() {
    executeQuery(fixture.getSut(httpStatusCode = 403))

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it)
          assertEquals(SpanStatus.PERMISSION_DENIED, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `creates a span around the request failing with network error`() {
    executeQuery(fixture.getSut(socketPolicy = SocketPolicy.DISCONNECT_DURING_REQUEST_BODY))

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it)
          assertEquals(SpanStatus.INTERNAL_ERROR, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `handles non-ascii header values correctly`() {
    executeQuery(id = "á")

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it)
          assertEquals(SpanStatus.OK, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `adds breadcrumb when http calls succeeds`() {
    executeQuery(fixture.getSut())
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          // response_body_size is added but mock webserver returns 0 always
          assertEquals(0L, it.data["response_body_size"])
          assertEquals(193L, it.data["request_body_size"])
          assertEquals("query", it.data["operation_type"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `internal headers are not sent over the wire`() {
    executeQuery(fixture.getSut())
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recorderRequest.headers[SentryApollo3HttpInterceptor.SENTRY_APOLLO_3_VARIABLES])
    assertNull(recorderRequest.headers[SentryApollo3HttpInterceptor.SENTRY_APOLLO_3_OPERATION_TYPE])
  }

  private fun assertTransactionDetails(it: SentryTransaction) {
    assertEquals(1, it.spans.size)
    val httpClientSpan = it.spans.first()
    assertEquals("http.graphql.query", httpClientSpan.op)
    assertEquals("query LaunchDetails", httpClientSpan.description)
    assertEquals("auto.graphql.apollo3", httpClientSpan.origin)
    assertNotNull(httpClientSpan.data) {
      assertNotNull(it["operationId"])
      assertNotNull(it["variables"])
    }
  }

  private fun executeQuery(
    sut: ApolloClient = fixture.getSut(),
    isSpanActive: Boolean = true,
    id: String = "83",
  ) = runBlocking {
    var tx: ITransaction? = null
    if (isSpanActive) {
      tx =
        SentryTracer(TransactionContext("op", "desc", TracesSamplingDecision(true)), fixture.scopes)
      whenever(fixture.scopes.span).thenReturn(tx)
    }

    val coroutine = launch {
      try {
        sut.query(LaunchDetailsQuery(id)).execute()
      } catch (e: ApolloException) {
        return@launch
      }
    }

    coroutine.join()
    tx?.finish()
  }
}
