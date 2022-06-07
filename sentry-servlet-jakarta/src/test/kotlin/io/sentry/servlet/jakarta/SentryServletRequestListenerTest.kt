package io.sentry.servlet.jakarta

import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import jakarta.servlet.ServletRequestEvent
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
