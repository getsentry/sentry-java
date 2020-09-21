package io.sentry.spring

import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryOptions
import javax.servlet.FilterChain
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SentryRequestFilterTest {
    private class Fixture {
        val hub = mock<IHub>()
        val filter = SentryRequestFilter(hub, SentryOptions())
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val filterChain = mock<FilterChain>()
    }

    private val fixture = Fixture()

    @Test
    fun `pushes and pops scope in correct order`() {
        fixture.filter.doFilterInternal(fixture.request, fixture.response, fixture.filterChain)

        val inOrder = inOrder(fixture.filterChain, fixture.hub)
        inOrder.verify(fixture.hub).pushScope()
        inOrder.verify(fixture.filterChain).doFilter(fixture.request, fixture.response)
        inOrder.verify(fixture.hub).popScope()
    }

    @Test
    fun `adds breadcrumb when request gets initialized`() {
        fixture.request.requestURI = "http://localhost:8080/some-uri"
        fixture.request.method = "post"

        fixture.filter.doFilterInternal(fixture.request, fixture.response, fixture.filterChain)

        verify(fixture.hub).addBreadcrumb(check { it: Breadcrumb ->
            assertEquals("http://localhost:8080/some-uri", it.getData("url"))
            assertEquals("POST", it.getData("method"))
            assertEquals("http", it.type)
        })
    }
}
