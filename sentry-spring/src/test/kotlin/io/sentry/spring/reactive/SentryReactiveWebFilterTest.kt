package io.sentry.spring.reactive

import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryOptions
import javax.servlet.ServletRequestEvent
import kotlin.test.Test
import org.assertj.core.api.Assertions
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain

class SentryReactiveWebFilterTest {

    private class Fixture {
        val baseHub = mock<IHub>()
        val hub = mock<IHub>()
        val filter = SentryReactiveWebFilter(baseHub, SentryOptions())
        val request = MockServerHttpRequest
            .post("http://localhost:8080/some-uri")
            .build()
        val event = mock<ServletRequestEvent>()
        val webExchange = MockServerWebExchange.from(request)
        val chain = mock<WebFilterChain>()

        init {
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
            Assertions.assertThat(it.getData("url")).isEqualTo("/some-uri")
            Assertions.assertThat(it.getData("method")).isEqualTo("POST")
            Assertions.assertThat(it.type).isEqualTo("http")
        })
    }

    @Test
    fun `Add the cloned Hub as Exchange attribute`() {
        fixture.filter.filter(fixture.webExchange, fixture.chain)
        val iHub = fixture.webExchange.getAttribute<IHub>(SentryReactiveWebHelper.REQUEST_HUB_ATTR_NAME)
        Assertions.assertThat(iHub).isSameAs(fixture.hub)
    }
}
