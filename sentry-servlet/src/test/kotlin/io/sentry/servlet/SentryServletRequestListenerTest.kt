package io.sentry.servlet

import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import javax.servlet.ServletRequestEvent
import kotlin.test.Test
import kotlin.test.assertSame

class SentryServletRequestListenerTest {
    private class Fixture {
        val scopes = mock<IScopes>()
        val lifecycleToken = mock<ISentryLifecycleToken>()
        val listener = SentryServletRequestListener(scopes)
        val request = MockHttpServletRequest()
        val event = mock<ServletRequestEvent>()

        init {
            request.requestURI = "http://localhost:8080/some-uri"
            request.method = "post"
            whenever(event.servletRequest).thenReturn(request)
            whenever(scopes.pushIsolationScope()).thenReturn(lifecycleToken)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `pushes scope when request gets initialized`() {
        fixture.listener.requestInitialized(fixture.event)

        verify(fixture.scopes).pushIsolationScope()
    }

    @Test
    fun `adds breadcrumb when request gets initialized`() {
        fixture.listener.requestInitialized(fixture.event)

        verify(fixture.scopes).addBreadcrumb(
            check { it: Breadcrumb ->
                assertThat(it.getData("url")).isEqualTo("http://localhost:8080/some-uri")
                assertThat(it.getData("method")).isEqualTo("POST")
                assertThat(it.type).isEqualTo("http")
            },
            anyOrNull()
        )
        assertSame(fixture.lifecycleToken, fixture.request.getAttribute("sentry-lifecycle"))
    }

    @Test
    fun `pops scope when request gets destroyed`() {
        fixture.request.setAttribute("sentry-lifecycle", fixture.lifecycleToken)

        fixture.listener.requestDestroyed(fixture.event)
        verify(fixture.lifecycleToken).close()
    }
}
