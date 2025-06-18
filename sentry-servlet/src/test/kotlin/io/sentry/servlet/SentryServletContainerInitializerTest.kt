package io.sentry.servlet

import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.EventListener
import javax.servlet.ServletContext
import kotlin.test.Test

class SentryServletContainerInitializerTest {
    private val initializer = SentryServletContainerInitializer()

    @Test
    fun `adds SentryServletRequestListener on startup`() {
        val servletContext = mock<ServletContext>()
        initializer.onStartup(null, servletContext)
        verify(servletContext).addListener(
            check { it: Class<out EventListener> ->
                assertThat(it).isEqualTo(SentryServletRequestListener::class.java)
            },
        )
    }
}
