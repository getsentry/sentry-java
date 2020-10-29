package io.sentry.spring

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
import org.springframework.mock.web.MockHttpServletRequest

class SentrySpringRequestListenerTest {
    private class Fixture {
        val hub = mock<IHub>()
        val listener = SentrySpringRequestListener(hub, SentryRequestResolver(SentryOptions()))
        val request = MockHttpServletRequest()
        val event = mock<ServletRequestEvent>()

        init {
            request.requestURI = "http://localhost:8080/some-uri"
            request.method = "post"
            whenever(event.servletRequest).thenReturn(request)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `pushes scope when request gets initialized`() {
        fixture.listener.requestInitialized(fixture.event)

        verify(fixture.hub).pushScope()
    }

    @Test
    fun `adds breadcrumb when request gets initialized`() {
        fixture.listener.requestInitialized(fixture.event)

        verify(fixture.hub).addBreadcrumb(check { it: Breadcrumb ->
            Assertions.assertThat(it.getData("url")).isEqualTo("http://localhost:8080/some-uri")
            Assertions.assertThat(it.getData("method")).isEqualTo("POST")
            Assertions.assertThat(it.type).isEqualTo("http")
        })
    }

    @Test
    fun `pops scope when request gets destroyed`() {
        fixture.listener.requestDestroyed(fixture.event)

        verify(fixture.hub).popScope()
    }
}
