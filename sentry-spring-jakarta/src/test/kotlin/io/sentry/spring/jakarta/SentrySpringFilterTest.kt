package io.sentry.spring.jakarta

import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryOptions.RequestSize.ALWAYS
import io.sentry.SentryOptions.RequestSize.MEDIUM
import io.sentry.SentryOptions.RequestSize.NONE
import io.sentry.SentryOptions.RequestSize.SMALL
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.web.util.ContentCachingRequestWrapper
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SentrySpringFilterTest {
    private class Fixture {
        val scopes = mock<IScopes>()
        val scopesBeforeForking = mock<IScopes>()
        val response = MockHttpServletResponse()
        val lifecycleToken = mock<ISentryLifecycleToken>()
        val chain = mock<FilterChain>()
        lateinit var scope: IScope
        lateinit var request: HttpServletRequest

        fun getSut(
            request: HttpServletRequest? = null,
            options: SentryOptions = SentryOptions(),
        ): SentrySpringFilter {
            scope = Scope(options)
            whenever(scopesBeforeForking.options).thenReturn(options)
            whenever(scopesBeforeForking.isEnabled).thenReturn(true)
            whenever(scopes.options).thenReturn(options)
            whenever(scopes.isEnabled).thenReturn(true)
            whenever(scopesBeforeForking.forkedScopes(any())).thenReturn(scopes)
            whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(scopes).configureScope(any())
            this.request = request
                ?: MockHttpServletRequest().apply {
                    this.requestURI = "http://localhost:8080/some-uri"
                    this.method = "post"
                }
            return SentrySpringFilter(scopesBeforeForking)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `pushes scope when request gets initialized`() {
        val listener = fixture.getSut()
        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.scopesBeforeForking).forkedScopes(any())
        verify(fixture.scopes).makeCurrent()
    }

    @Test
    fun `adds breadcrumb when request gets initialized`() {
        val listener = fixture.getSut()
        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.scopes).addBreadcrumb(
            check { it: Breadcrumb ->
                Assertions.assertThat(it.getData("url")).isEqualTo("http://localhost:8080/some-uri")
                Assertions.assertThat(it.getData("method")).isEqualTo("POST")
                Assertions.assertThat(it.type).isEqualTo("http")
            },
            anyOrNull(),
        )
    }

    @Test
    fun `pops scope when request gets destroyed`() {
        val listener = fixture.getSut()
        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.lifecycleToken).close()
    }

    @Test
    fun `pops scope when chain throws`() {
        val listener = fixture.getSut()
        whenever(fixture.chain.doFilter(any(), any())).thenThrow(RuntimeException())

        try {
            listener.doFilter(fixture.request, fixture.response, fixture.chain)
            fail()
        } catch (e: Exception) {
            verify(fixture.lifecycleToken).close()
        }
    }

    @Test
    fun `attaches basic information from HTTP request to Scope request`() {
        val listener =
            fixture.getSut(
                request =
                    MockMvcRequestBuilders
                        .get(URI.create("http://example.com?param1=xyz"))
                        .header("some-header", "some-header value")
                        .accept(MediaType.APPLICATION_JSON)
                        .buildRequest(MockServletContext()),
            )

        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.request) {
            assertEquals("GET", it.method)
            assertEquals(
                mapOf(
                    "some-header" to "some-header value",
                    "Accept" to "application/json",
                ),
                it.headers,
            )
            assertEquals("http://example.com", it.url)
            assertEquals("param1=xyz", it.queryString)
        }
    }

    @Test
    fun `attaches header with multiple values to Scope request`() {
        val listener =
            fixture.getSut(
                request =
                    MockMvcRequestBuilders
                        .get(URI.create("http://example.com?param1=xyz"))
                        .header("another-header", "another value")
                        .header("another-header", "another value2")
                        .buildRequest(MockServletContext()),
            )

        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.request) {
            assertEquals(
                mapOf(
                    "another-header" to "another value,another value2",
                ),
                it.headers,
            )
        }
    }

    @Test
    fun `when sendDefaultPii is set to true, attaches filtered cookies to Scope request`() {
        val sentryOptions =
            SentryOptions().apply {
                isSendDefaultPii = true
            }

        val listener =
            fixture.getSut(
                request =
                    MockMvcRequestBuilders
                        .get(URI.create("http://example.com?param1=xyz"))
                        .header("Cookie", "name=value; JSESSIONID=123; mysessioncookiename=789")
                        .header("Cookie", "name2=value2; SID=456")
                        .buildRequest(servletContextWithCustomCookieName("mysessioncookiename")),
                options = sentryOptions,
            )

        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.request) {
            val expectedCookieString =
                "name=value; JSESSIONID=[Filtered]; mysessioncookiename=[Filtered],name2=value2; SID=[Filtered]"
            assertEquals(expectedCookieString, it.cookies)
            assertEquals(expectedCookieString, it.headers!!["Cookie"])
        }
    }

    @Test
    fun `when sendDefaultPii is set to false, does not attach cookies to Scope request`() {
        val sentryOptions =
            SentryOptions().apply {
                isSendDefaultPii = false
            }

        val listener =
            fixture.getSut(
                request =
                    MockMvcRequestBuilders
                        .get(URI.create("http://example.com?param1=xyz"))
                        .header("Cookie", "name=value")
                        .buildRequest(MockServletContext()),
                options = sentryOptions,
            )

        listener.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.request) {
            assertNull(it.cookies)
        }
    }

    @Test
    fun `when sendDefaultPii is set to false, does not attach sensitive headers`() {
        val sentryOptions =
            SentryOptions().apply {
                isSendDefaultPii = false
            }

        val listener =
            fixture.getSut(
                request =
                    MockMvcRequestBuilders
                        .get(URI.create("http://example.com?param1=xyz"))
                        .header("some-header", "some-header value")
                        .header("X-FORWARDED-FOR", "192.168.0.1")
                        .header("authorization", "Token")
                        .header("Authorization", "Token")
                        .header("Cookie", "some cookies")
                        .buildRequest(MockServletContext()),
                options = sentryOptions,
            )

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
        data class TestParams(
            val sendDefaultPii: Boolean = true,
            val maxRequestBodySize: SentryOptions.RequestSize,
            val body: String,
            val contentType: String = "application/json",
            val expectedToBeCached: Boolean,
        )

        val params =
            listOf(
                TestParams(maxRequestBodySize = NONE, body = "xxx", expectedToBeCached = false),
                TestParams(maxRequestBodySize = SMALL, body = "xxx", expectedToBeCached = false, sendDefaultPii = false),
                TestParams(maxRequestBodySize = SMALL, body = "xxx", expectedToBeCached = true),
                TestParams(maxRequestBodySize = SMALL, body = "xxx", contentType = "application/octet-stream", expectedToBeCached = false),
                TestParams(maxRequestBodySize = SMALL, body = "x".repeat(1001), expectedToBeCached = false),
                TestParams(maxRequestBodySize = MEDIUM, body = "x".repeat(1001), expectedToBeCached = true),
                TestParams(maxRequestBodySize = MEDIUM, body = "x".repeat(10001), expectedToBeCached = false),
                TestParams(maxRequestBodySize = ALWAYS, body = "x".repeat(10001), expectedToBeCached = true),
                TestParams(
                    maxRequestBodySize = SMALL,
                    body = "xxx",
                    contentType = "application/x-www-form-urlencoded",
                    expectedToBeCached = true,
                ),
            )

        params.forEach { param ->
            try {
                val fixture = Fixture()
                val sentryOptions =
                    SentryOptions().apply {
                        maxRequestBodySize = param.maxRequestBodySize
                        isSendDefaultPii = param.sendDefaultPii
                    }

                val listener =
                    fixture.getSut(
                        request =
                            MockMvcRequestBuilders
                                .post(URI.create("http://example.com?param1=xyz"))
                                .content(param.body)
                                .contentType(param.contentType)
                                .buildRequest(MockServletContext()),
                        options = sentryOptions,
                    )

                listener.doFilter(fixture.request, fixture.response, fixture.chain)

                verify(fixture.chain).doFilter(
                    check {
                        assertEquals(param.expectedToBeCached, it is ContentCachingRequestWrapper)
                    },
                    any(),
                )
            } catch (e: AssertionError) {
                System.err.println("Failed to run test with params: $param")
                throw e
            }
        }
    }

    private fun servletContextWithCustomCookieName(name: String): ServletContext =
        MockServletContext().also {
            it.sessionCookieConfig.name = name
        }
}
