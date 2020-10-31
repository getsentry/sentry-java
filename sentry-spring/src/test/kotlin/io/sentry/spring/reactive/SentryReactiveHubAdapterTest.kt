package io.sentry.spring.reactive

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.IHub
import java.lang.RuntimeException
import kotlin.test.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.test.StepVerifier

class SentryReactiveHubAdapterTest {

    private class Fixture {
        val hub = mock<IHub>()
        val request: MockServerHttpRequest = MockServerHttpRequest
            .post("http://localhost:8080/some-uri")
            .build()
        val webExchange: MockServerWebExchange = MockServerWebExchange.from(request)

        fun getSut(): SentryReactiveHubAdapter {
            return SentryReactiveHubAdapter(hub, emptyList(), webExchange)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `Should capture event with hub`() {
        fixture.getSut().captureWith { t ->
            t.captureMessage("Hello!")
        }.`as` { StepVerifier.create(it) }.verifyComplete()

        verify(fixture.hub).captureMessage("Hello!")
    }

    @Test
    fun `Configure scope with providers when called`() {
        fixture.getSut().captureWith { t ->
            t.captureException(RuntimeException("Error"))
        }.`as` { StepVerifier.create(it) }.verifyComplete()

        verify(fixture.hub).configureScope(any())
    }
}
