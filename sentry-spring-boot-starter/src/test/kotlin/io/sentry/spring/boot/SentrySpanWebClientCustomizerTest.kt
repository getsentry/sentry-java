package io.sentry.spring.boot

import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import kotlin.test.Test
import kotlin.test.assertEquals


class SentrySpanWebClientCustomizerTest {
    class Fixture {
        val sentryOptions = SentryOptions()
        val hub = mock<IHub>()
        var mockServer = MockWebServer()
        val transaction = SentryTracer(TransactionContext("aTransaction", "op", true), hub)
        private val customizer = SentrySpanWebClientCustomizer(hub)

        init {
            whenever(hub.options).thenReturn(sentryOptions)
        }

        fun getSut(isTransactionActive: Boolean, status: HttpStatus = HttpStatus.OK, throwIOException: Boolean = false): WebClient {
            val webClientBuilder = WebClient.builder()
            customizer.customize(webClientBuilder)
            val webClient = webClientBuilder.build()

            if (isTransactionActive) {
                val scope = Scope(sentryOptions)
                scope.transaction = transaction
                whenever(hub.span).thenReturn(transaction)
            }

            val dispatcher: Dispatcher = object : Dispatcher() {
                @Throws(InterruptedException::class)
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (isTransactionActive) {
                        assertThat(request.headers["sentry-trace"]!!)
                            .startsWith(transaction.spanContext.traceId.toString())
                            .endsWith("-1")
                            .doesNotContain(transaction.spanContext.spanId.toString())
                        if (throwIOException) {
                            return MockResponse().setResponseCode(500)
                                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        } else {
                            return MockResponse().setResponseCode(status.value()).setBody("OK")
                                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        }
                    } else {
                        return MockResponse().setResponseCode(status.value()).setBody("OK")
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
        val result = fixture.getSut(isTransactionActive = true)
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
    fun `when transaction is active and response code is not 2xx, creates span with error status around WebClient HTTP call`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        try {
            fixture.getSut(isTransactionActive = true, status = HttpStatus.INTERNAL_SERVER_ERROR)
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
            fixture.getSut(isTransactionActive = true, throwIOException = true)
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
        val result = fixture.getSut(isTransactionActive = false)
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
        fixture.getSut(isTransactionActive = true)
            .post()
            .uri(uri)
            .body(BodyInserters.fromValue("content"))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("http", it.type)
            assertEquals(uri.toString(), it.data["url"])
            assertEquals("POST", it.data["method"])
        })
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `when transaction is active adds breadcrumb when http calls results in exception`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        try {
            fixture.getSut(isTransactionActive = true, status = HttpStatus.INTERNAL_SERVER_ERROR)
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (e: Throwable) {
        }
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("http", it.type)
            assertEquals(uri.toString(), it.data["url"])
            assertEquals("GET", it.data["method"])
        })
    }

    @Test
    fun `when transaction is not active adds breadcrumb when http calls succeeds`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        fixture.getSut(isTransactionActive = false)
            .post()
            .uri(uri)
            .body(BodyInserters.fromValue("content"))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("http", it.type)
            assertEquals(uri.toString(), it.data["url"])
            assertEquals("POST", it.data["method"])
        })
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `when transaction is not active adds breadcrumb when http calls results in exception`() {
        val uri = fixture.mockServer.url("/test/123").toUri()
        try {
            fixture.getSut(isTransactionActive = false, status = HttpStatus.INTERNAL_SERVER_ERROR)
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (e: Throwable) {
        }
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("http", it.type)
            assertEquals(uri.toString(), it.data["url"])
            assertEquals("GET", it.data["method"])
        })
    }
}
