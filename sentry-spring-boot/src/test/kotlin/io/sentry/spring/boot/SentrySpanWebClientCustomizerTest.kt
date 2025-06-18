package io.sentry.spring.boot

import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.Sentry.OptionsConfiguration
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.mockServerRequestTimeoutMillis
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentrySpanWebClientCustomizerTest {
    class Fixture {
        lateinit var sentryOptions: SentryOptions
        lateinit var scope: IScope
        val scopes = mock<IScopes>()
        var mockServer = MockWebServer()
        lateinit var transaction: SentryTracer
        private val customizer = SentrySpanWebClientCustomizer(scopes)

        fun getSut(
            isTransactionActive: Boolean,
            status: HttpStatus = HttpStatus.OK,
            throwIOException: Boolean = false,
            includeMockServerInTracingOrigins: Boolean = true,
            optionsConfiguration: OptionsConfiguration<SentryOptions>? = null,
        ): WebClient {
            sentryOptions =
                SentryOptions().also {
                    optionsConfiguration?.configure(it)
                    if (includeMockServerInTracingOrigins) {
                        it.setTracePropagationTargets(listOf(mockServer.hostName))
                    } else {
                        it.setTracePropagationTargets(listOf("other-api"))
                    }
                    it.dsn = "http://key@localhost/proj"
                }
            scope = Scope(sentryOptions)
            whenever(scopes.options).thenReturn(sentryOptions)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(scopes).configureScope(
                any(),
            )
            transaction = SentryTracer(TransactionContext("aTransaction", "op", TracesSamplingDecision(true)), scopes)
            val webClientBuilder = WebClient.builder()
            customizer.customize(webClientBuilder)
            val webClient = webClientBuilder.build()

            if (isTransactionActive) {
                val scope = Scope(sentryOptions)
                scope.transaction = transaction
                whenever(scopes.span).thenReturn(transaction)
            }

            val dispatcher: Dispatcher =
                object : Dispatcher() {
                    @Throws(InterruptedException::class)
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (isTransactionActive && includeMockServerInTracingOrigins) {
                            assertThat(request.headers["sentry-trace"]!!)
                                .startsWith(transaction.spanContext.traceId.toString())
                                .endsWith("-1")
                                .doesNotContain(transaction.spanContext.spanId.toString())
                            return if (throwIOException) {
                                MockResponse()
                                    .setResponseCode(500)
                                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            } else {
                                MockResponse()
                                    .setResponseCode(status.value())
                                    .setBody("OK")
                                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            }
                        } else {
                            return MockResponse()
                                .setResponseCode(status.value())
                                .setBody("OK")
                                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        }
                    }
                }
            mockServer.dispatcher = dispatcher
            return webClient
        }
    }

    private val fixture = Fixture()

    @BeforeEach
    fun setUp() {
        fixture.mockServer.start()
    }

    @AfterEach
    fun tearDown() {
        fixture.mockServer.shutdown()
    }

    @Test
    fun `when transaction is active, creates span around WebClient HTTP call`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        val result =
            fixture
                .getSut(isTransactionActive = true)
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        assertThat(result).isEqualTo("OK")
        assertThat(fixture.transaction.spans).hasSize(1)
        val span = fixture.transaction.spans.first()
        assertThat(span.operation).isEqualTo("http.client")
        assertThat(span.description).isEqualTo("GET $uri")
        assertThat(span.status).isEqualTo(SpanStatus.OK)
    }

    @Test
    fun `when transaction is active and server is not listed in tracing origins, does not add sentry trace header to the request`() {
        fixture
            .getSut(isTransactionActive = true, includeMockServerInTracingOrigins = false)
            .get()
            .uri(fixture.mockServer.url("/test/123").toUri())
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        val recordedRequest = fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when no transaction is active and server is not listed in tracing origins, does not add sentry trace header to the request`() {
        fixture
            .getSut(isTransactionActive = false, includeMockServerInTracingOrigins = false)
            .get()
            .uri(fixture.mockServer.url("/test/123").toUri())
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        val recordedRequest = fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when no transaction is active, adds sentry trace header to the request from scope`() {
        fixture
            .getSut(isTransactionActive = false, includeMockServerInTracingOrigins = true)
            .get()
            .uri(fixture.mockServer.url("/test/123").toUri())
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        val recordedRequest = fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNotNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `does not add sentry-trace header when span origin is ignored`() {
        val sut =
            fixture.getSut(isTransactionActive = false, includeMockServerInTracingOrigins = true) { options ->
                options.setIgnoredSpanOrigins(listOf("auto.http.spring.webclient"))
            }
        sut
            .get()
            .uri(fixture.mockServer.url("/test/123").toUri())
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        val recordedRequest = fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when transaction is active and server is listed in tracing origins, adds sentry trace header to the request`() {
        fixture
            .getSut(isTransactionActive = true)
            .get()
            .uri(fixture.mockServer.url("/test/123").toUri())
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        val recordedRequest = fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNotNull(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when transaction is active and response code is not 2xx, creates span with error status around WebClient HTTP call`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        try {
            fixture
                .getSut(isTransactionActive = true, status = HttpStatus.INTERNAL_SERVER_ERROR)
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (e: Throwable) {
        }
        assertThat(fixture.transaction.spans).hasSize(1)
        val span = fixture.transaction.spans.first()
        assertThat(span.operation).isEqualTo("http.client")
        assertThat(span.description).isEqualTo("GET $uri")
        assertThat(span.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
    }

    @Test
    fun `when transaction is active and throws IO exception, creates span with error status around WebClient HTTP call`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        try {
            fixture
                .getSut(isTransactionActive = true, throwIOException = true)
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (e: Throwable) {
        }
        assertThat(fixture.transaction.spans).hasSize(1)
        val span = fixture.transaction.spans.first()
        assertThat(span.operation).isEqualTo("http.client")
        assertThat(span.description).isEqualTo("GET $uri")
        assertThat(span.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
    }

    @Test
    fun `when transaction is not active, does not create span around WebClient HTTP call`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        val result =
            fixture
                .getSut(isTransactionActive = false)
                .get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono { response -> response.bodyToMono(String::class.java) }
                .block()

        assertThat(result).isEqualTo("OK")
        assertThat(fixture.transaction.spans).isEmpty()
    }

    @Test
    fun `when transaction is active adds breadcrumb when http calls succeeds`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        fixture
            .getSut(isTransactionActive = true)
            .post()
            .uri(uri)
            .body(BodyInserters.fromValue("content"))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(uri.toString(), it.data["url"])
                assertEquals("POST", it.data["method"])
            },
            anyOrNull(),
        )
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `when transaction is active adds breadcrumb when http calls results in exception`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        try {
            fixture
                .getSut(isTransactionActive = true, status = HttpStatus.INTERNAL_SERVER_ERROR)
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (e: Throwable) {
        }
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(uri.toString(), it.data["url"])
                assertEquals("GET", it.data["method"])
            },
            anyOrNull(),
        )
    }

    @Test
    fun `when transaction is not active adds breadcrumb when http calls succeeds`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        fixture
            .getSut(isTransactionActive = false)
            .post()
            .uri(uri)
            .body(BodyInserters.fromValue("content"))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(uri.toString(), it.data["url"])
                assertEquals("POST", it.data["method"])
            },
            anyOrNull(),
        )
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `when transaction is not active adds breadcrumb when http calls results in exception`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        try {
            fixture
                .getSut(isTransactionActive = false, status = HttpStatus.INTERNAL_SERVER_ERROR)
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (e: Throwable) {
        }
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(uri.toString(), it.data["url"])
                assertEquals("GET", it.data["method"])
            },
            anyOrNull(),
        )
    }
}
