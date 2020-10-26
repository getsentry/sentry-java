package io.sentry.spring.reactive

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.IHub
import kotlin.test.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.test.StepVerifier

class SentryReactiveErrorAttributesTest {

    private val request = MockServerHttpRequest.get("http://example.com").build()
    private val exchange = MockServerWebExchange.from(request)
    private val hub = mock<IHub>()

    @Test
    fun `Capture exception with Request Hub`() {
        exchange.attributes[SentryReactiveWebHelper.REQUEST_HUB_ATTR_NAME] = hub

        val errorAttributes = SentryReactiveExceptionHandler()
        val exception = RuntimeException("Sample Exception")

        StepVerifier.create(errorAttributes.handle(exchange, exception))
            .verifyError()

        verify(hub).captureException(exception)
    }

    @Test
    fun `Should not throw exception when there is no hub`() {

        val errorAttributes = SentryReactiveExceptionHandler()
        val exception = RuntimeException("Sample Exception")
        StepVerifier.create(errorAttributes.handle(exchange, exception))
            .verifyError()

        verify(hub, never()).captureException(exception)
    }
}
