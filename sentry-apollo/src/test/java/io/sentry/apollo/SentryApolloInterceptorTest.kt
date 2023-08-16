package io.sentry.apollo

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.coroutines.await
import com.apollographql.apollo.exception.ApolloException
import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TraceContext
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryTransaction
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryApolloInterceptorTest {

    class Fixture {
        val server = MockWebServer()
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            sdkVersion = SdkVersion("test", "1.2.3")
        }
        val scope = Scope(options)
        val hub = mock<IHub>().also {
            whenever(it.options).thenReturn(options)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(it).configureScope(
                any()
            )
        }
        private var interceptor = SentryApolloInterceptor(hub)

        @SuppressWarnings("LongParameterList")
        fun getSut(
            httpStatusCode: Int = 200,
            responseBody: String = """{
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
            beforeSpan: SentryApolloInterceptor.BeforeSpanCallback? = null
        ): ApolloClient {
            server.enqueue(
                MockResponse()
                    .setBody(responseBody)
                    .setSocketPolicy(socketPolicy)
                    .setResponseCode(httpStatusCode)
            )

            if (beforeSpan != null) {
                interceptor = SentryApolloInterceptor(hub, beforeSpan)
            }
            return ApolloClient.builder()
                .serverUrl(server.url("/"))
                .addApplicationInterceptor(interceptor)
                .build()
        }
    }

    private val fixture = Fixture()

    @Test
    fun `creates a span around the successful request`() {
        executeQuery()

        verify(fixture.hub).captureTransaction(
            check {
                assertTransactionDetails(it)
                assertEquals(SpanStatus.OK, it.spans.first().status)
                assertEquals("POST", it.spans.first().data?.get(SpanDataConvention.HTTP_METHOD_KEY))
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `creates a span around the failed request`() {
        executeQuery(fixture.getSut(httpStatusCode = 403))

        verify(fixture.hub).captureTransaction(
            check {
                assertTransactionDetails(it)
                assertEquals(SpanStatus.PERMISSION_DENIED, it.spans.first().status)
                assertEquals(403, it.spans.first().data?.get(SpanDataConvention.HTTP_STATUS_CODE_KEY))
                // we do not have access to the request and method in case of an error
                assertNull(it.spans.first().data?.get(SpanDataConvention.HTTP_METHOD_KEY))
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `creates a span around the request failing with network error`() {
        executeQuery(fixture.getSut(socketPolicy = SocketPolicy.DISCONNECT_DURING_REQUEST_BODY))

        verify(fixture.hub).captureTransaction(
            check {
                assertTransactionDetails(it)
                assertEquals(SpanStatus.INTERNAL_ERROR, it.spans.first().status)
                assertNull(it.spans.first().data?.get(SpanDataConvention.HTTP_STATUS_CODE_KEY))
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when there is no active span, adds sentry trace header to the request from scope`() {
        executeQuery(isSpanActive = false)

        val recorderRequest = fixture.server.takeRequest()
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when there is an active span, adds sentry trace headers to the request`() {
        executeQuery()
        val recorderRequest = fixture.server.takeRequest()
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `customizer modifies span`() {
        executeQuery(
            fixture.getSut { span, _, _ ->
                span.description = "overwritten description"
                span
            }
        )

        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(1, it.spans.size)
                val httpClientSpan = it.spans.first()
                assertEquals("overwritten description", httpClientSpan.description)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when customizer throws, exception is handled`() {
        executeQuery(
            fixture.getSut { _, _, _ -> throw RuntimeException() }
        )

        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(1, it.spans.size)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `adds breadcrumb when http calls succeeds`() {
        executeQuery()
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(280L, it.data["response_body_size"])
                assertEquals(193L, it.data["request_body_size"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `sets SDKVersion Info`() {
        assertNotNull(fixture.hub.options.sdkVersion)
        assert(fixture.hub.options.sdkVersion!!.integrationSet.contains("Apollo"))
        val packageInfo = fixture.hub.options.sdkVersion!!.packageSet.firstOrNull { pkg -> pkg.name == "maven:io.sentry:sentry-apollo" }
        assertNotNull(packageInfo)
        assert(packageInfo.version == BuildConfig.VERSION_NAME)
    }

    private fun assertTransactionDetails(it: SentryTransaction) {
        assertEquals(1, it.spans.size)
        val httpClientSpan = it.spans.first()
        assertEquals("http.graphql.query", httpClientSpan.op)
        assertEquals("query LaunchDetails", httpClientSpan.description)
        assertEquals("auto.graphql.apollo", httpClientSpan.origin)
        assertNotNull(httpClientSpan.data) {
            assertNotNull(it["operationId"])
            assertEquals("{id=83}", it["variables"])
        }
    }

    private fun executeQuery(sut: ApolloClient = fixture.getSut(), isSpanActive: Boolean = true) = runBlocking {
        var tx: ITransaction? = null
        if (isSpanActive) {
            tx = SentryTracer(TransactionContext("op", "desc", TracesSamplingDecision(true)), fixture.hub)
            whenever(fixture.hub.span).thenReturn(tx)
        }

        val coroutine = launch {
            try {
                sut.query(LaunchDetailsQuery.builder().id("83").build()).await()
            } catch (e: ApolloException) {
                return@launch
            }
        }
        coroutine.join()
        tx?.finish()
    }
}
