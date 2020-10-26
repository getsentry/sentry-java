package io.sentry.spring.reactive

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.IHub
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.core.Ordered
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.test.StepVerifier

class SentryReactiveExceptionHandlerTest {

    private val request = MockServerHttpRequest.get("http://example.com").build()
    private val exchange = MockServerWebExchange.from(request)
    private val hub = mock<IHub>()

    @Test
    fun `Capture exception with Request Hub`() {
        val adapter = SentryReactiveHubAdapter(hub, emptyList(), exchange)
        exchange.attributes[SentryReactiveWebHelper.REQUEST_HUB_ADAPTER_NAME] = adapter

        val exceptionHandler = SentryReactiveExceptionHandler()
        val exception = RuntimeException("Sample Exception")

        StepVerifier.create(exceptionHandler.handle(exchange, exception))
            .verifyError()

        verify(hub).captureEvent(any())
    }

    @Test
    fun `Should indicate highest precedence order`() {
        val exceptionHandler = SentryReactiveExceptionHandler()
        assertEquals(Ordered.HIGHEST_PRECEDENCE, exceptionHandler.order)
    }
}
