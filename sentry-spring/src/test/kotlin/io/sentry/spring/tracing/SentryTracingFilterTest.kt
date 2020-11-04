package io.sentry.spring.tracing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTransaction
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.TransactionContexts
import io.sentry.protocol.SentryId
import io.sentry.spring.SentryRequestResolver
import javax.servlet.FilterChain
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping

class SentryTracingFilterTest {
    private class Fixture {
        val hub = mock<IHub>()
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()
        val requestResolver = SentryRequestResolver(SentryOptions())

        fun getSut(sentryTraceHeader: String? = null): SentryTracingFilter {
            request.requestURI = "/product/12"
            request.method = "POST"
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/product/{id}")
            if (sentryTraceHeader != null) {
                request.addHeader("sentry-trace", sentryTraceHeader)
                whenever(hub.startTransaction(any(), any<TransactionContexts>())).thenAnswer { SentryTransaction(it.arguments[0] as String, it.arguments[1] as TransactionContexts, hub) }
            }
            response.status = 200
            whenever(hub.startTransaction(any())).thenAnswer { SentryTransaction(it.arguments[0] as String, TransactionContexts(), hub) }
            return SentryTracingFilter(hub, SentryOptions(), requestResolver)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `creates transaction around the request`() {
        val filter = fixture.getSut()

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.chain).doFilter(fixture.request, fixture.response)
        verify(fixture.hub).captureTransaction(check {
            assertThat(it.transaction).isEqualTo("POST /product/{id}")
            assertThat(it.contexts.traceContext.status).isEqualTo(SpanStatus.OK)
            assertThat(it.contexts.traceContext.op).isEqualTo("http")
            assertThat(it.request).isNotNull()
        }, eq(null))
    }

    @Test
    fun `when sentry trace is not present, transaction does not have parentSpanId set`() {
        val filter = fixture.getSut()

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).captureTransaction(check {
            assertThat(it.contexts.traceContext.parentSpanId).isNull()
        }, eq(null))
    }

    @Test
    fun `when sentry trace is present, transaction has parentSpanId set`() {
        val parentSpanId = SpanId()
        val filter = fixture.getSut(sentryTraceHeader = "${SentryId()}-$parentSpanId-1")

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).captureTransaction(check {
            assertThat(it.contexts.traceContext.parentSpanId).isEqualTo(parentSpanId)
        }, eq(null))
    }
}
