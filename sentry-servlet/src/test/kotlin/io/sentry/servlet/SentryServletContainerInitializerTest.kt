package io.sentry.servlet

import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import java.util.EventListener
import javax.servlet.ServletContext
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat

class SentryServletContainerInitializerTest {

    private val initializer = SentryServletContainerInitializer()

    @Test
    fun `adds SentryServletRequestListener on startup`() {
        val servletContext = mock<ServletContext>()
        initializer.onStartup(null, servletContext)
        verify(servletContext).addListener(check { it: Class<out EventListener> ->
            assertThat(it).isEqualTo(SentryServletRequestListener::class.java)
        })
    }
}
