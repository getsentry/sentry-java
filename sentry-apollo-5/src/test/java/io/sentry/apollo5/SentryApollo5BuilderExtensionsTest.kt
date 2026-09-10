package io.sentry.apollo5

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.http.DefaultHttpRequestComposer
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpRequestComposer
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.network.http.HttpInterceptor
import com.apollographql.apollo.network.http.HttpInterceptorChain
import com.apollographql.apollo.network.http.HttpNetworkTransport
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TraceContext
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.apollo5.SentryApollo5HttpInterceptor.BeforeSpanCallback
import io.sentry.apollo5.generated.LaunchDetailsQuery
import io.sentry.mockServerRequestTimeoutMillis
import io.sentry.protocol.SentryTransaction
import java.util.concurrent.TimeUnit
import kotlin.reflect.KSuspendFunction1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryApollo5BuilderExtensionsTestWithV5Implementation :
  SentryApollo5BuilderExtensionsTest(ApolloCall<*>::execute)

abstract class SentryApollo5BuilderExtensionsTest(
  private val executeQueryImplementation:
    KSuspendFunction1<ApolloCall<*>, ApolloResponse<out Operation.Data>>
) {
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
      interceptor: HttpInterceptor? = null,
    ): ApolloClient {
      whenever(scopes.options)
        .thenReturn(SentryOptions().apply { dsn = "http://key@localhost/proj" })

      server.enqueue(
        MockResponse()
          .setBody(responseBody)
          .setSocketPolicy(socketPolicy)
          .setResponseCode(httpStatusCode)
      )

      val builder =
        ApolloClient.Builder()
          .serverUrl(server.url("/").toString())
          // keep the request body deterministic across Apollo versions for exact body assertions
          .sendEnhancedClientAwareness(false)
          .sentryTracing(scopes = scopes, beforeSpan = beforeSpan, captureFailedRequests = false)

      interceptor?.let { builder.addHttpInterceptor(it) }

      return builder.build()
    }

    fun getSutWithCustomNetworkTransport(
      manuallyInstallHttpInterceptor: Boolean,
      useCustomRequestComposer: Boolean = false,
    ): ApolloClient {
      whenever(scopes.options)
        .thenReturn(SentryOptions().apply { dsn = "http://key@localhost/proj" })
      server.enqueue(MockResponse().setBody("{\"data\":{\"launch\":null}}"))

      val serverUrl = server.url("/").toString()
      val transportBuilder = HttpNetworkTransport.Builder()
      if (useCustomRequestComposer) {
        val defaultComposer = DefaultHttpRequestComposer(serverUrl)
        transportBuilder.httpRequestComposer(
          object : HttpRequestComposer {
            override fun <D : Operation.Data> compose(
              apolloRequest: ApolloRequest<D>
            ): HttpRequest {
              val request = defaultComposer.compose(apolloRequest)
              return HttpRequest.Builder(request.method, request.url)
                .addHeaders(request.headers)
                .apply { request.body?.let { body(it) } }
                .addExecutionContext(apolloRequest.executionContext)
                .build()
            }
          }
        )
      } else {
        transportBuilder.serverUrl(serverUrl)
      }
      if (manuallyInstallHttpInterceptor) {
        transportBuilder.addInterceptor(
          SentryApollo5HttpInterceptor(scopes = scopes, captureFailedRequests = false)
        )
      }

      val builder = ApolloClient.Builder().networkTransport(transportBuilder.build())
      return if (manuallyInstallHttpInterceptor) {
        builder
          .addInterceptor(
            SentryApollo5Interceptor(scopes),
            ApolloInterceptor.InsertionPoint.BeforeCache,
          )
          .build()
      } else {
        builder.sentryTracing(scopes = scopes, captureFailedRequests = false).build()
      }
    }
  }

  private val fixture = Fixture()

  @Test
  fun `sentryTracing installs both interceptors`() {
    val builder = ApolloClient.Builder().sentryTracing(captureFailedRequests = false)

    assertThat(builder.interceptors.filterIsInstance<SentryApollo5Interceptor>()).hasSize(1)
    assertThat(builder.httpInterceptors.filterIsInstance<SentryApollo5HttpInterceptor>()).hasSize(1)
  }

  @Test
  fun `creates span around successful request`() {
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
  fun `creates span around failed request`() {
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
  fun `creates span around request failing with network error`() {
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
    verify(fixture.scopes, never()).captureEvent(any<SentryEvent>(), any<Hint>())
  }

  @Test
  fun `sentryTracing fails with a custom network transport`() {
    assertFailsWith<IllegalStateException> {
      fixture.getSutWithCustomNetworkTransport(manuallyInstallHttpInterceptor = false)
    }
  }

  @Test
  fun `custom network transport is instrumented when HTTP interceptor is installed manually`() {
    executeQuery(fixture.getSutWithCustomNetworkTransport(manuallyInstallHttpInterceptor = true))

    verify(fixture.scopes)
      .captureTransaction(
        check { assertTransactionDetails(it) },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `custom request composer propagates operation context when copied explicitly`() {
    executeQuery(
      fixture.getSutWithCustomNetworkTransport(
        manuallyInstallHttpInterceptor = true,
        useCustomRequestComposer = true,
      )
    )

    verify(fixture.scopes)
      .captureTransaction(
        check { assertTransactionDetails(it) },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `default request composer propagates operation context`() {
    var operationContext: SentryApollo5OperationContext? = null
    val recordingInterceptor =
      object : HttpInterceptor {
        override suspend fun intercept(
          request: HttpRequest,
          chain: HttpInterceptorChain,
        ): HttpResponse {
          operationContext = request.executionContext[SentryApollo5OperationContext]
          return chain.proceed(request)
        }
      }

    executeQuery(
      fixture.getSut(interceptor = recordingInterceptor),
      initialOperationContext =
        SentryApollo5OperationContext(
          operationId = "stale-id",
          operationName = "StaleOperation",
          operationType = "mutation",
          variables = null,
        ),
    )

    assertThat(operationContext)
      .isEqualTo(
        SentryApollo5OperationContext(
          operationId = LaunchDetailsQuery.OPERATION_ID,
          operationName = LaunchDetailsQuery.OPERATION_NAME,
          operationType = "query",
          variables = "{id=83}",
        )
      )
  }

  @Test
  fun `adds breadcrumb when http call succeeds`() {
    executeQuery(fixture.getSut())

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals(200, it.data["status_code"])
          // response_body_size is added but mock webserver returns 0 always
          assertEquals(0L, it.data["response_body_size"])
          assertEquals(193L, it.data["request_body_size"])
          assertEquals("LaunchDetails", it.data["operation_name"])
          assertEquals("query", it.data["operation_type"])
          assertEquals(LaunchDetailsQuery.OPERATION_ID, it.data["operation_id"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `adds breadcrumb when http call fails`() {
    executeQuery(fixture.getSut(socketPolicy = SocketPolicy.DISCONNECT_DURING_REQUEST_BODY))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals(193L, it.data["request_body_size"])
          assertEquals("LaunchDetails", it.data["operation_name"])
          assertEquals("query", it.data["operation_type"])
          assertEquals(LaunchDetailsQuery.OPERATION_ID, it.data["operation_id"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `handles non-ascii variables correctly`() {
    executeQuery(id = "á")

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it, expectedVariables = "{id=á}")
          assertEquals(SpanStatus.OK, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `does not send internal headers over the wire`() {
    executeQuery(fixture.getSut())
    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!

    assertThat(
        recordedRequest.headers.names().none {
          it.startsWith("SENTRY-APOLLO-5-", ignoreCase = true)
        }
      )
      .isTrue()
  }

  private fun assertTransactionDetails(
    it: SentryTransaction,
    expectedVariables: String = "{id=83}",
  ) {
    assertEquals(1, it.spans.size)
    val httpClientSpan = it.spans.first()
    assertEquals("http.graphql.query", httpClientSpan.op)
    assertEquals("query LaunchDetails", httpClientSpan.description)
    assertEquals("auto.graphql.apollo5", httpClientSpan.origin)
    assertNotNull(httpClientSpan.data) {
      assertEquals(LaunchDetailsQuery.OPERATION_ID, it["operationId"])
      assertEquals(expectedVariables, it["variables"])
    }
  }

  private fun executeQuery(
    sut: ApolloClient = fixture.getSut(),
    isSpanActive: Boolean = true,
    id: String = "83",
    initialOperationContext: SentryApollo5OperationContext? = null,
  ) = runBlocking {
    var tx: ITransaction? = null
    if (isSpanActive) {
      tx =
        SentryTracer(TransactionContext("op", "desc", TracesSamplingDecision(true)), fixture.scopes)
      whenever(fixture.scopes.span).thenReturn(tx)
    }

    val coroutine = launch {
      try {
        val call = sut.query(LaunchDetailsQuery(id))
        initialOperationContext?.let { call.addExecutionContext(it) }
        executeQueryImplementation(call)
      } catch (e: ApolloException) {
        return@launch
      }
    }

    coroutine.join()
    tx?.finish()
  }
}
