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
import io.sentry.SentryOptions.RequestSize.ALWAYS
import io.sentry.SentryOptions.RequestSize.MEDIUM
import io.sentry.SentryOptions.RequestSize.NONE
import io.sentry.SentryOptions.RequestSize.SMALL
import java.net.URI
import javax.servlet.FilterChain
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
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

class SentrySpringFilterTest {
    private class Fixture {
        val hub = mock<IHub>()
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()
        lateinit var scope: Scope
        lateinit var request: HttpServletRequest

        fun getSut(request: HttpServletRequest? = null, options: SentryOptions = SentryOptions()): SentrySpringFilter {
            scope = Scope(options)
            whenever(hub.options).thenReturn(options)
            whenever(hub.isEnabled).thenReturn(true)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(hub).configureScope(any())
            this.request = request
                    ?: MockHttpServletRequest().apply {
                        this.requestURI = "http://localhost:8080/some-uri"
                        this.method = "post"
                    }
            return SentrySpringFilter(hub)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `pushes scope when request gets initialized`() {
        val listener = fixture.getSut()
        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).pushScope()
    }

    @Test
    fun `adds breadcrumb when request gets initialized`() {
        val listener = fixture.getSut()
        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).addBreadcrumb(check { it: Breadcrumb ->
            Assertions.assertThat(it.getData("url")).isEqualTo("http://localhost:8080/some-uri")
            Assertions.assertThat(it.getData("method")).isEqualTo("POST")
            Assertions.assertThat(it.type).isEqualTo("http")
        })
    }

    @Test
    fun `pops scope when request gets destroyed`() {
        val listener = fixture.getSut()
        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).popScope()
    }

    @Test
    fun `attaches basic information from HTTP request to Scope request`() {
        val listener = fixture.getSut(request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("some-header", "some-header value")
            .accept(MediaType.APPLICATION_JSON)
            .buildRequest(MockServletContext()))

        listener.doFilter(fixture.request, fixture.response, fixture.chain)

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

        listener.doFilter(fixture.request, fixture.response, fixture.chain)

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

        listener.doFilter(fixture.request, fixture.response, fixture.chain)

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

        listener.doFilter(fixture.request, fixture.response, fixture.chain)

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

        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.request) { request ->
            assertNotNull(request.headers) {
                assertFalse(it.containsKey("X-FORWARDED-FOR"))
                assertFalse(it.containsKey("Authorization"))
                assertFalse(it.containsKey("authorization"))
                assertFalse(it.containsKey("Cookie"))
                assertTrue(it.containsKey("some-header"))
            }
        }
    }

    @Test
    fun `caches request depending on the maxRequestBodySize value and request body length`() {
        data class TestParams(val sendDefaultPii: Boolean = true, val maxRequestBodySize: SentryOptions.RequestSize, val body: String, val contentType: String = "application/json", val expectedToBeCached: Boolean)

        val params = listOf(
            TestParams(maxRequestBodySize = NONE, body = "xxx", expectedToBeCached = false),
            TestParams(maxRequestBodySize = SMALL, body = "xxx", expectedToBeCached = false, sendDefaultPii = false),
            TestParams(maxRequestBodySize = SMALL, body = "xxx", expectedToBeCached = true),
            TestParams(maxRequestBodySize = SMALL, body = "xxx", contentType = "application/octet-stream", expectedToBeCached = false),
            TestParams(maxRequestBodySize = SMALL, body = "x".repeat(1001), expectedToBeCached = false),
            TestParams(maxRequestBodySize = MEDIUM, body = "x".repeat(1001), expectedToBeCached = true),
            TestParams(maxRequestBodySize = MEDIUM, body = "x".repeat(10001), expectedToBeCached = false),
            TestParams(maxRequestBodySize = ALWAYS, body = "x".repeat(10001), expectedToBeCached = true)
        )

        params.forEach { param ->
            try {
                val fixture = Fixture()
                val sentryOptions = SentryOptions().apply {
                    maxRequestBodySize = param.maxRequestBodySize
                    isSendDefaultPii = param.sendDefaultPii
                }

                val listener = fixture.getSut(request = MockMvcRequestBuilders
                    .post(URI.create("http://example.com?param1=xyz"))
                    .content(param.body)
                    .contentType(param.contentType)
                    .buildRequest(MockServletContext()), options = sentryOptions)

                listener.doFilter(fixture.request, fixture.response, fixture.chain)

                verify(fixture.chain).doFilter(check {
                    assertEquals(param.expectedToBeCached, it is CachedBodyHttpServletRequest)
                }, any())
            } catch (e: AssertionError) {
                System.err.println("Failed to run test with params: $param")
                throw e
            }
        }
    }
}
