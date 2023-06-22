package io.sentry.spring.jakarta.webflux

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.ILogger
import io.sentry.PropagationContext
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.TraceContext
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.TransactionNameSource
import io.sentry.spring.jakarta.webflux.AbstractSentryWebFilter.SENTRY_HUB_KEY
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.WebFilterChain
import org.springframework.web.util.pattern.PathPatternParser
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SentryWebFluxTracingFilterTest {
    private class Fixture {
        val hub = mock<IHub>()
        lateinit var request: MockServerHttpRequest
        lateinit var exchange: MockServerWebExchange
        val chain = mock<WebFilterChain>()
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            enableTracing = true
        }
        val logger = mock<ILogger>()

        init {
            whenever(hub.options).thenReturn(options)
        }

        fun getSut(isEnabled: Boolean = true, status: HttpStatus = HttpStatus.OK, sentryTraceHeader: String? = null, baggageHeaders: List<String>? = null, method: HttpMethod = HttpMethod.POST): SentryWebFilter {
            var requestBuilder = MockServerHttpRequest.method(method, "/product/{id}", 12)
            if (sentryTraceHeader != null) {
                requestBuilder = requestBuilder.header("sentry-trace", sentryTraceHeader)
                whenever(hub.startTransaction(any(), check<TransactionOptions> { it.isBindToScope })).thenAnswer { SentryTracer(it.arguments[0] as TransactionContext, hub) }
            }
            if (baggageHeaders != null) {
                requestBuilder = requestBuilder.header("baggage", *baggageHeaders.toTypedArray())
            }
            request = requestBuilder.build()
            exchange = MockServerWebExchange.builder(request).build()
            exchange.attributes.put(SENTRY_HUB_KEY, hub)
            exchange.attributes.put(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, PathPatternParser().parse("/product/{id}"))
            exchange.response.statusCode = status
            whenever(hub.startTransaction(any(), check<TransactionOptions> { assertTrue(it.isBindToScope) })).thenAnswer { SentryTracer(it.arguments[0] as TransactionContext, hub) }
            whenever(hub.isEnabled).thenReturn(isEnabled)
            whenever(chain.filter(any())).thenReturn(Mono.create { s -> s.success() })
            whenever(hub.continueTrace(anyOrNull(), anyOrNull())).thenAnswer { TransactionContext.fromPropagationContext(PropagationContext.fromHeaders(logger, it.arguments[0] as String?, it.arguments[1] as List<String>?)) }
            return SentryWebFilter(hub)
        }
    }

    private val fixture = Fixture()

    fun withMockHub(closure: () -> Unit) = Mockito.mockStatic(Sentry::class.java).use {
        it.`when`<Any> { Sentry.cloneMainHub() }.thenReturn(fixture.hub)
        closure.invoke()
    }

    @Test
    fun `creates transaction around the request`() {
        val filter = fixture.getSut()
        withMockHub {
            filter.filter(fixture.exchange, fixture.chain).block()

            verify(fixture.hub).startTransaction(
                check<TransactionContext> {
                    assertEquals("POST /product/12", it.name)
                    assertEquals(TransactionNameSource.URL, it.transactionNameSource)
                    assertEquals("http.server", it.operation)
                },
                check<TransactionOptions> {
                    assertNotNull(it.customSamplingContext?.get("request"))
                    assertTrue(it.customSamplingContext?.get("request") is ServerHttpRequest)
                    assertTrue(it.isBindToScope)
                }
            )
            verify(fixture.chain).filter(fixture.exchange)
            verify(fixture.hub).captureTransaction(
                check {
                    assertThat(it.transaction).isEqualTo("POST /product/{id}")
                    assertThat(it.contexts.trace!!.status).isEqualTo(SpanStatus.OK)
                    assertThat(it.contexts.trace!!.operation).isEqualTo("http.server")
                },
                anyOrNull<TraceContext>(),
                anyOrNull(),
                anyOrNull()
            )
        }
    }

    @Test
    fun `sets correct span status based on the response status`() {
        val filter = fixture.getSut(status = HttpStatus.INTERNAL_SERVER_ERROR)

        withMockHub {
            filter.filter(fixture.exchange, fixture.chain).block()

            verify(fixture.hub).captureTransaction(
                check {
                    assertThat(it.contexts.trace!!.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
                },
                anyOrNull<TraceContext>(),
                anyOrNull(),
                anyOrNull()
            )
        }
    }

    @Test
    fun `does not set span status for response status that dont match predefined span statuses`() {
        val filter = fixture.getSut(status = HttpStatus.FOUND)

        withMockHub {
            filter.filter(fixture.exchange, fixture.chain).block()

            verify(fixture.hub).captureTransaction(
                check {
                    assertThat(it.contexts.trace!!.status).isNull()
                },
                anyOrNull<TraceContext>(),
                anyOrNull(),
                anyOrNull()
            )
        }
    }

    @Test
    fun `when sentry trace is not present, transaction does not have parentSpanId set`() {
        val filter = fixture.getSut()

        withMockHub {
            filter.filter(fixture.exchange, fixture.chain).block()

            verify(fixture.hub).captureTransaction(
                check {
                    assertThat(it.contexts.trace!!.parentSpanId).isNull()
                },
                anyOrNull<TraceContext>(),
                anyOrNull(),
                anyOrNull()
            )
        }
    }

    @Test
    fun `when sentry trace is present, transaction has parentSpanId set`() {
        val parentSpanId = SpanId()
        val filter = fixture.getSut(sentryTraceHeader = "${SentryId()}-$parentSpanId-1")

        withMockHub {
            filter.filter(fixture.exchange, fixture.chain).block()

            verify(fixture.hub).captureTransaction(
                check {
                    assertThat(it.contexts.trace!!.parentSpanId).isEqualTo(parentSpanId)
                },
                anyOrNull<TraceContext>(),
                anyOrNull(),
                anyOrNull()
            )
        }
    }

    @Test
    fun `when hub is disabled, components are not invoked`() {
        val filter = fixture.getSut(isEnabled = false)

        withMockHub {
            filter.filter(fixture.exchange, fixture.chain).block()

            verify(fixture.chain).filter(fixture.exchange)

            verify(fixture.hub, times(3)).isEnabled
            verifyNoMoreInteractions(fixture.hub)
        }
    }

    @Test
    fun `sets status to internal server error when chain throws exception`() {
        val filter = fixture.getSut()

        withMockHub {
            whenever(fixture.chain.filter(any())).thenReturn(Mono.error(RuntimeException("error")))

            try {
                filter.filter(fixture.exchange, fixture.chain).block()
                fail("filter is expected to rethrow exception")
            } catch (_: Exception) {
            }
            verify(fixture.hub).captureTransaction(
                check {
                    assertThat(it.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
                },
                anyOrNull<TraceContext>(),
                anyOrNull(),
                anyOrNull()
            )
        }
    }

    @Test
    fun `does not track OPTIONS request with traceOptionsRequests=false`() {
        val filter = fixture.getSut(method = HttpMethod.OPTIONS)

        withMockHub {
            fixture.options.isTraceOptionsRequests = false

            filter.filter(fixture.exchange, fixture.chain).block()

            verify(fixture.chain).filter(fixture.exchange)

            verify(fixture.hub, times(3)).isEnabled
            verify(fixture.hub, times(2)).options
            verify(fixture.hub).continueTrace(anyOrNull(), anyOrNull())
            verify(fixture.hub).pushScope()
            verify(fixture.hub).addBreadcrumb(any<Breadcrumb>(), any<Hint>())
            verify(fixture.hub).configureScope(any<ScopeCallback>())
            verify(fixture.hub).popScope()
            verifyNoMoreInteractions(fixture.hub)
        }
    }

    @Test
    fun `tracks OPTIONS request with traceOptionsRequests=true`() {
        val filter = fixture.getSut(method = HttpMethod.OPTIONS)

        withMockHub {
            fixture.options.isTraceOptionsRequests = true

            filter.filter(fixture.exchange, fixture.chain).block()

            verify(fixture.chain).filter(fixture.exchange)

            verify(fixture.hub).captureTransaction(
                check {
                    assertThat(it.contexts.trace!!.parentSpanId).isNull()
                },
                anyOrNull<TraceContext>(),
                anyOrNull(),
                anyOrNull()
            )
        }
    }

    @Test
    fun `tracks POST request with traceOptionsRequests=false`() {
        val filter = fixture.getSut(method = HttpMethod.POST)

        withMockHub {
            fixture.options.isTraceOptionsRequests = false

            filter.filter(fixture.exchange, fixture.chain).block()

            verify(fixture.chain).filter(fixture.exchange)

            verify(fixture.hub).captureTransaction(
                check {
                    assertThat(it.contexts.trace!!.parentSpanId).isNull()
                },
                anyOrNull<TraceContext>(),
                anyOrNull(),
                anyOrNull()
            )
        }
    }

    @Test
    fun `continues incoming trace even is performance is disabled`() {
        val parentSpanId = SpanId()
        val sentryTraceHeaderString = "2722d9f6ec019ade60c776169d9a8904-$parentSpanId-1"
        val baggageHeaderStrings = listOf("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET")
        fixture.options.enableTracing = false
        val filter = fixture.getSut(sentryTraceHeader = sentryTraceHeaderString, baggageHeaders = baggageHeaderStrings)

        withMockHub {
            filter.filter(fixture.exchange, fixture.chain).block()

            verify(fixture.chain).filter(fixture.exchange)

            verify(fixture.hub, never()).captureTransaction(
                anyOrNull<SentryTransaction>(),
                anyOrNull<TraceContext>(),
                anyOrNull(),
                anyOrNull()
            )

            verify(fixture.hub).continueTrace(eq(sentryTraceHeaderString), eq(baggageHeaderStrings))
        }
    }
}
