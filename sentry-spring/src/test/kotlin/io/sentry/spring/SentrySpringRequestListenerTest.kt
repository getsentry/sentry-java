package io.sentry.spring

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import java.net.URI
import javax.servlet.ServletRequestEvent
import javax.servlet.http.HttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.assertj.core.api.Assertions
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

class SentrySpringRequestListenerTest {
    private class Fixture {
        val hub = mock<IHub>()
        val event = mock<ServletRequestEvent>()
        lateinit var scope: Scope

        fun getSut(request: HttpServletRequest? = null, options: SentryOptions = SentryOptions()): SentrySpringRequestListener {
            scope = Scope(options)
            whenever(hub.options).thenReturn(options)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(hub).configureScope(any())

            val r = request ?: MockHttpServletRequest().apply {
                this.requestURI = "http://localhost:8080/some-uri"
                this.method = "post"
            }
            whenever(event.servletRequest).thenReturn(r)

            return SentrySpringRequestListener(hub)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `pushes scope when request gets initialized`() {
        val listener = fixture.getSut()
        listener.requestInitialized(fixture.event)

        verify(fixture.hub).pushScope()
    }

    @Test
    fun `adds breadcrumb when request gets initialized`() {
        val listener = fixture.getSut()
        listener.requestInitialized(fixture.event)

        verify(fixture.hub).addBreadcrumb(check { it: Breadcrumb ->
            Assertions.assertThat(it.getData("url")).isEqualTo("http://localhost:8080/some-uri")
            Assertions.assertThat(it.getData("method")).isEqualTo("POST")
            Assertions.assertThat(it.type).isEqualTo("http")
        })
    }

    @Test
    fun `pops scope when request gets destroyed`() {
        val listener = fixture.getSut()
        listener.requestDestroyed(fixture.event)

        verify(fixture.hub).popScope()
    }

    @Test
    fun `attaches basic information from HTTP request to Scope request`() {
        val listener = fixture.getSut(request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("some-header", "some-header value")
            .accept(MediaType.APPLICATION_JSON)
            .buildRequest(MockServletContext()))

        listener.requestInitialized(fixture.event)

        assertNotNull(fixture.scope.request) {
            assertEquals("GET", it.method)
            assertEquals(mapOf(
                "some-header" to "some-header value",
                "Accept" to "application/json"
            ), it.headers)
            assertEquals("http://example.com", it.url)
            assertEquals("param1=xyz", it.queryString)
        }
    }

    @Test
    fun `attaches header with multiple values to Scope request`() {
        val listener = fixture.getSut(request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("another-header", "another value")
            .header("another-header", "another value2")
            .buildRequest(MockServletContext()))

        listener.requestInitialized(fixture.event)

        assertNotNull(fixture.scope.request) {
            assertEquals(mapOf(
                "another-header" to "another value,another value2"
            ), it.headers)
        }
    }

    @Test
    fun `when sendDefaultPii is set to true, attaches cookies information to Scope request`() {
        val sentryOptions = SentryOptions().apply {
            isSendDefaultPii = true
        }

        val listener = fixture.getSut(request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("Cookie", "name=value")
            .header("Cookie", "name2=value2")
            .buildRequest(MockServletContext()), options = sentryOptions)

        listener.requestInitialized(fixture.event)

        assertNotNull(fixture.scope.request) {
            assertEquals("name=value,name2=value2", it.cookies)
        }
    }

    @Test
    fun `when sendDefaultPii is set to false, does not attach cookies to Scope request`() {
        val sentryOptions = SentryOptions().apply {
            isSendDefaultPii = false
        }

        val listener = fixture.getSut(request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("Cookie", "name=value")
            .buildRequest(MockServletContext()), options = sentryOptions)

        listener.requestInitialized(fixture.event)

        assertNotNull(fixture.scope.request) {
            assertNull(it.cookies)
        }
    }

    @Test
    fun `when sendDefaultPii is set to false, does not attach sensitive headers`() {
        val sentryOptions = SentryOptions().apply {
            isSendDefaultPii = false
        }

        val listener = fixture.getSut(request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("some-header", "some-header value")
            .header("X-FORWARDED-FOR", "192.168.0.1")
            .header("authorization", "Token")
            .header("Authorization", "Token")
            .header("Cookie", "some cookies")
            .buildRequest(MockServletContext()), options = sentryOptions)

        listener.requestInitialized(fixture.event)

        assertNotNull(fixture.scope.request) {
            assertFalse(it.headers.containsKey("X-FORWARDED-FOR"))
            assertFalse(it.headers.containsKey("Authorization"))
            assertFalse(it.headers.containsKey("authorization"))
            assertFalse(it.headers.containsKey("Cookie"))
            assertTrue(it.headers.containsKey("some-header"))
        }
    }
}
