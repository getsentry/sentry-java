package io.sentry.servlet.jakarta

import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import jakarta.servlet.ServletContext
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
            }
        )
    }
}
