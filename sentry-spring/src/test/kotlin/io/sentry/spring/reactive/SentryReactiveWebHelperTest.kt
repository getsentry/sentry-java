package io.sentry.spring.reactive

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.IHub
import kotlin.test.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class SentryReactiveWebHelperTest {

    private class Fixture {
        val hub = mock<IHub>()

        val request: MockServerHttpRequest = MockServerHttpRequest
            .post("http://localhost:8080/some-uri")
            .build()
        val webExchange: MockServerWebExchange = MockServerWebExchange.from(request)
        val adapter = SentryReactiveHubAdapter(hub, emptyList(), webExchange)
    }
    private val fixture = Fixture()

    @Test
    fun `Should capture event with context hub`() {
        Mono.just("").flatMap {
            SentryReactiveWebHelper.captureWithRequestHub {
                it.captureMessage("Hello!")
            }
        }.subscriberContext(SentryReactiveHubContextHolder.withSentryHub(fixture.adapter))
            .`as` { StepVerifier.create(it) }
            .verifyComplete()

        verify(fixture.hub).captureMessage("Hello!")
    }

    @Test
    fun `Should capture event with request hub`() {
        fixture.webExchange.attributes[SentryReactiveWebHelper.REQUEST_HUB_ADAPTER_NAME] = fixture.adapter
        SentryReactiveWebHelper.captureWithRequestHub(fixture.webExchange) {
            it.captureMessage("Hello!")
        }.`as` { StepVerifier.create(it) }.verifyComplete()

        verify(fixture.hub).captureMessage("Hello!")
    }
}
