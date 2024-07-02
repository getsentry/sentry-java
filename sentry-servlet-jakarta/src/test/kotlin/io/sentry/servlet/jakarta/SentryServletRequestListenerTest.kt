package io.sentry.servlet.jakarta

import io.sentry.Breadcrumb
import io.sentry.IHub
import jakarta.servlet.ServletRequestEvent
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryServletRequestListenerTest {
    private class Fixture {
        val hub = mock<IHub>()
        val listener =
            SentryServletRequestListener(hub)
        val request = mockRequest(
            url = "http://localhost:8080/some-uri",
            method = "POST"
        )
        val event = mock<ServletRequestEvent>()

        init {
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

        verify(fixture.hub).addBreadcrumb(
            check { it: Breadcrumb ->
                assertEquals("/some-uri", it.getData("url"))
                assertEquals("POST", it.getData("method"))
                assertEquals("http", it.type)
            },
            anyOrNull()
        )
    }

    @Test
    fun `pops scope when request gets destroyed`() {
        fixture.listener.requestDestroyed(fixture.event)

        verify(fixture.hub).popScope()
    }
}
