package io.sentry.servlet

import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import javax.servlet.ServletRequestEvent
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.mock.web.MockHttpServletRequest

class SentryServletRequestListenerTest {
    private class Fixture {
        val hub = mock<IHub>()
        val listener = SentryServletRequestListener(hub)
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
            assertThat(it.getData("url")).isEqualTo("http://localhost:8080/some-uri")
            assertThat(it.getData("method")).isEqualTo("POST")
            assertThat(it.type).isEqualTo("http")
        })
    }

    @Test
    fun `pops scope when request gets destroyed`() {
        fixture.listener.requestDestroyed(fixture.event)

        verify(fixture.hub).popScope()
    }
}
