package io.sentry.servlet.jakarta

import jakarta.servlet.ServletContext
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.EventListener
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryServletContainerInitializerTest {
    private val initializer =
        SentryServletContainerInitializer()

    @Test
    fun `adds SentryServletRequestListener on startup`() {
        val servletContext = mock<ServletContext>()
        initializer.onStartup(null, servletContext)
        verify(servletContext).addListener(
            check { it: Class<out EventListener> ->
                assertEquals(SentryServletRequestListener::class.java, it)
            },
        )
    }
}
