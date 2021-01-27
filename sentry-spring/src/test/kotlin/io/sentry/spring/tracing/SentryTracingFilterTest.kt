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
import io.sentry.SpanContext
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.protocol.SentryId
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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

        init {
            whenever(hub.options).thenReturn(SentryOptions())
        }

        fun getSut(sentryTraceHeader: String? = null): SentryTracingFilter {
            request.requestURI = "/product/12"
            request.method = "POST"
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/product/{id}")
            if (sentryTraceHeader != null) {
                request.addHeader("sentry-trace", sentryTraceHeader)
                whenever(hub.startTransaction(any<TransactionContext>(), any())).thenAnswer { SentryTransaction((it.arguments[0] as TransactionContext).name, it.arguments[0] as SpanContext, hub) }
            }
            response.status = 200
            whenever(hub.startTransaction(any<String>(), any())).thenAnswer { SentryTransaction(it.arguments[0] as String, SpanContext(), hub) }
            return SentryTracingFilter(hub)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `creates transaction around the request`() {
        val filter = fixture.getSut()

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).startTransaction(eq("POST /product/12"), check {
            assertNotNull(it["request"])
            assertTrue(it["request"] is HttpServletRequest)
        })
        verify(fixture.chain).doFilter(fixture.request, fixture.response)
        verify(fixture.hub).captureTransaction(check {
            assertThat(it.transaction).isEqualTo("POST /product/{id}")
            assertThat(it.contexts.trace!!.status).isEqualTo(SpanStatus.OK)
            assertThat(it.contexts.trace!!.operation).isEqualTo("http.server")
            assertThat(it.request).isNotNull()
        }, eq(null))
    }

    @Test
    fun `when sentry trace is not present, transaction does not have parentSpanId set`() {
        val filter = fixture.getSut()

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).captureTransaction(check {
            assertThat(it.contexts.trace!!.parentSpanId).isNull()
        }, eq(null))
    }

    @Test
    fun `when sentry trace is present, transaction has parentSpanId set`() {
        val parentSpanId = SpanId()
        val filter = fixture.getSut(sentryTraceHeader = "${SentryId()}-$parentSpanId-1")

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).captureTransaction(check {
            assertThat(it.contexts.trace!!.parentSpanId).isEqualTo(parentSpanId)
        }, eq(null))
    }
}
