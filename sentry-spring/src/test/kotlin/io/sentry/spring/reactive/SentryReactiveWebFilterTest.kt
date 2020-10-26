package io.sentry.spring.reactive

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryOptions
import javax.servlet.ServletRequestEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class SentryReactiveWebFilterTest {

    private class Fixture {
        val baseHub = mock<IHub>()
        val hub = mock<IHub>()
        val filter = SentryReactiveWebFilter(baseHub, SentryOptions(), emptyList())
        val request: MockServerHttpRequest = MockServerHttpRequest
            .post("http://localhost:8080/some-uri")
            .build()
        val event = mock<ServletRequestEvent>()
        val webExchange: MockServerWebExchange = MockServerWebExchange.from(request)
        val chain = mock<WebFilterChain>()

        init {
            whenever(chain.filter(webExchange)).thenReturn(Mono.empty())
            whenever(baseHub.clone()).thenReturn(hub)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `clones hub when request arrives`() {
        fixture.filter.filter(fixture.webExchange, fixture.chain)

        verify(fixture.baseHub).clone()
        verify(fixture.hub).pushScope()
    }

    @Test
    fun `adds breadcrumb when request gets initialized`() {
        fixture.filter.filter(fixture.webExchange, fixture.chain)

        verify(fixture.hub).addBreadcrumb(check { it: Breadcrumb ->
            assertEquals("/some-uri", it.getData("url"))
            assertEquals("POST", it.getData("method"))
            assertEquals("http", it.type)
        })
    }

    @Test
    fun `Add the cloned Hub as Exchange attribute`() {
        fixture.filter.filter(fixture.webExchange, fixture.chain)
        val adapter = fixture.webExchange.getAttribute<SentryReactiveHubAdapter>(SentryReactiveWebHelper.REQUEST_HUB_ADAPTER_NAME)
        assertSame(fixture.hub, adapter.hub)
    }

    @Test
    fun `adds event processors when request gets initialized`() {
        fixture.filter.filter(fixture.webExchange, fixture.chain)
        verify(fixture.hub).configureScope(any())
    }

    @Test
    fun `Pop scope on terminate signal`() {
        fixture.filter
            .filter(fixture.webExchange, fixture.chain)
            .`as` { StepVerifier.create(it) }
            .verifyComplete()
        verify(fixture.hub).popScope()
    }
}
