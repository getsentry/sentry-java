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
import io.sentry.SpanStatus
import io.sentry.TransactionContexts
import io.sentry.spring.SentryRequestResolver
import javax.servlet.FilterChain
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SentryTracingFilterTest {
    private class Fixture {
        val hub = mock<IHub>()
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()
        val requestResolver = SentryRequestResolver(SentryOptions())

        init {
            request.requestURI = "/some-uri"
            request.method = "POST"
            response.status = 200
            whenever(hub.startTransaction(any())).thenAnswer { SentryTransaction(it.arguments[0] as String, TransactionContexts(), hub) }
        }

        fun getSut() = SentryTracingFilter(hub, requestResolver)
    }

    private val fixture = Fixture()

    @Test
    fun `creates transaction around the request`() {
        val filter = fixture.getSut()

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.chain).doFilter(fixture.request, fixture.response)
        verify(fixture.hub).captureTransaction(check {
            assertThat(it.transaction).isEqualTo("POST /some-uri")
            assertThat(it.contexts.traceContext.status).isEqualTo(SpanStatus.OK)
            assertThat(it.contexts.traceContext.op).isEqualTo("http")
            assertThat(it.request).isNotNull()
        }, eq(null))
    }
}
