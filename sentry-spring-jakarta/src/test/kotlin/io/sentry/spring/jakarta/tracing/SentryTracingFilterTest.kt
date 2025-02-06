package io.sentry.spring.jakarta.tracing

import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.PropagationContext
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
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SentryTracingFilterTest {
    private class Fixture {
        val scopes = mock<IScopes>()
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()
        val transactionNameProvider = mock<TransactionNameProvider>()
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            tracesSampleRate = 1.0
        }
        val logger = mock<ILogger>()

        init {
            whenever(scopes.options).thenReturn(options)
        }

        fun getSut(isEnabled: Boolean = true, status: Int = 200, sentryTraceHeader: String? = null, baggageHeaders: List<String>? = null): SentryTracingFilter {
            request.requestURI = "/product/12"
            request.method = "POST"
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/product/{id}")
            whenever(transactionNameProvider.provideTransactionName(request)).thenReturn("POST /product/{id}")
            whenever(transactionNameProvider.provideTransactionSource()).thenReturn(TransactionNameSource.CUSTOM)
            if (sentryTraceHeader != null) {
                request.addHeader("sentry-trace", sentryTraceHeader)
                whenever(scopes.startTransaction(any(), check<TransactionOptions> { it.isBindToScope })).thenAnswer { SentryTracer(it.arguments[0] as TransactionContext, scopes) }
            }
            if (baggageHeaders != null) {
                request.addHeader("baggage", baggageHeaders)
            }
            response.status = status
            whenever(scopes.startTransaction(any(), check<TransactionOptions> { assertTrue(it.isBindToScope) })).thenAnswer { SentryTracer(it.arguments[0] as TransactionContext, scopes) }
            whenever(scopes.isEnabled).thenReturn(isEnabled)
            whenever(scopes.continueTrace(any(), any())).thenAnswer { TransactionContext.fromPropagationContext(PropagationContext.fromHeaders(logger, it.arguments[0] as String?, it.arguments[1] as List<String>?)) }
            return SentryTracingFilter(scopes, transactionNameProvider)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `creates transaction around the request`() {
        val filter = fixture.getSut()

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.scopes).startTransaction(
            check<TransactionContext> {
                assertEquals("POST /product/12", it.name)
                assertEquals(TransactionNameSource.URL, it.transactionNameSource)
                assertEquals("http.server", it.operation)
            },
            check<TransactionOptions> {
                assertNotNull(it.customSamplingContext?.get("request"))
                assertTrue(it.customSamplingContext?.get("request") is HttpServletRequest)
                assertTrue(it.isBindToScope)
                assertThat(it.origin).isEqualTo("auto.http.spring_jakarta.webmvc")
            }
        )
        verify(fixture.chain).doFilter(fixture.request, fixture.response)
        verify(fixture.scopes).captureTransaction(
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

    @Test
    fun `sets correct span status based on the response status`() {
        val filter = fixture.getSut(status = 500)

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.scopes).captureTransaction(
            check {
                assertThat(it.contexts.trace!!.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `does not set span status for response status that dont match predefined span statuses`() {
        val filter = fixture.getSut(status = 507)

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.scopes).captureTransaction(
            check {
                assertThat(it.contexts.trace!!.status).isNull()
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when sentry trace is not present, transaction does not have parentSpanId set`() {
        val filter = fixture.getSut()

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.scopes).captureTransaction(
            check {
                assertThat(it.contexts.trace!!.parentSpanId).isNull()
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when sentry trace is present, transaction has parentSpanId set`() {
        val parentSpanId = SpanId()
        val filter = fixture.getSut(sentryTraceHeader = "${SentryId()}-$parentSpanId-1")

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.scopes).captureTransaction(
            check {
                assertThat(it.contexts.trace!!.parentSpanId).isEqualTo(parentSpanId)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when scopes is disabled, components are not invoked`() {
        val filter = fixture.getSut(isEnabled = false)

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.chain).doFilter(fixture.request, fixture.response)

        verify(fixture.scopes).isEnabled
        verifyNoMoreInteractions(fixture.scopes)
        verify(fixture.transactionNameProvider, never()).provideTransactionName(any())
    }

    @Test
    fun `sets status to internal server error when chain throws exception`() {
        val filter = fixture.getSut()
        whenever(fixture.chain.doFilter(any(), any())).thenThrow(RuntimeException("error"))

        try {
            filter.doFilter(fixture.request, fixture.response, fixture.chain)
            fail("filter is expected to rethrow exception")
        } catch (_: Exception) {
        }
        verify(fixture.scopes).captureTransaction(
            check {
                assertThat(it.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `does not track OPTIONS request with traceOptionsRequests=false`() {
        val filter = fixture.getSut()
        fixture.request.method = HttpMethod.OPTIONS.name()
        fixture.options.isTraceOptionsRequests = false

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.chain).doFilter(fixture.request, fixture.response)

        verify(fixture.scopes).isEnabled
        verify(fixture.scopes).continueTrace(anyOrNull(), anyOrNull())
        verify(fixture.scopes, times(3)).options
        verifyNoMoreInteractions(fixture.scopes)
        verify(fixture.transactionNameProvider, never()).provideTransactionName(any())
    }

    @Test
    fun `tracks OPTIONS request with traceOptionsRequests=true`() {
        val filter = fixture.getSut()
        fixture.request.method = HttpMethod.OPTIONS.name()
        fixture.options.isTraceOptionsRequests = true

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.chain).doFilter(fixture.request, fixture.response)

        verify(fixture.scopes).captureTransaction(
            check {
                assertThat(it.contexts.trace!!.parentSpanId).isNull()
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `tracks POST request with traceOptionsRequests=false`() {
        val filter = fixture.getSut()
        fixture.request.method = HttpMethod.POST.name()
        fixture.options.isTraceOptionsRequests = false

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.chain).doFilter(fixture.request, fixture.response)

        verify(fixture.scopes).captureTransaction(
            check {
                assertThat(it.contexts.trace!!.parentSpanId).isNull()
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `continues incoming trace even is performance is disabled`() {
        val parentSpanId = SpanId()
        val sentryTraceHeaderString = "2722d9f6ec019ade60c776169d9a8904-$parentSpanId-1"
        val baggageHeaderStrings = listOf("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET")
        fixture.options.tracesSampleRate = null
        val filter = fixture.getSut(sentryTraceHeader = sentryTraceHeaderString, baggageHeaders = baggageHeaderStrings)

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.chain).doFilter(fixture.request, fixture.response)

        verify(fixture.scopes).continueTrace(eq(sentryTraceHeaderString), eq(baggageHeaderStrings))

        verify(fixture.scopes, never()).captureTransaction(
            anyOrNull<SentryTransaction>(),
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `does not continue incoming trace if span origin is ignored`() {
        val parentSpanId = SpanId()
        val sentryTraceHeaderString = "2722d9f6ec019ade60c776169d9a8904-$parentSpanId-1"
        val baggageHeaderStrings = listOf("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET")
        fixture.options.tracesSampleRate = null
        fixture.options.setIgnoredSpanOrigins(listOf("auto.http.spring_jakarta.webmvc"))
        val filter = fixture.getSut(sentryTraceHeader = sentryTraceHeaderString, baggageHeaders = baggageHeaderStrings)

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.chain).doFilter(fixture.request, fixture.response)

        verify(fixture.scopes, never()).continueTrace(any(), any())

        verify(fixture.scopes, never()).captureTransaction(
            anyOrNull<SentryTransaction>(),
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }
}
