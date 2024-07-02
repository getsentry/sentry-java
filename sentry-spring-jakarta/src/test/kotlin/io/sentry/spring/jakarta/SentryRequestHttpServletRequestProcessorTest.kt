package io.sentry.spring.jakarta

import io.sentry.Hint
import io.sentry.IHub
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.spring.jakarta.tracing.SpringMvcTransactionNameProvider
import jakarta.servlet.http.HttpServletRequest
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.web.servlet.HandlerMapping
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SentryRequestHttpServletRequestProcessorTest {

    private class Fixture {
        val hub = mock<IHub>()

        fun getSut(request: HttpServletRequest, options: SentryOptions = SentryOptions()): SentryRequestHttpServletRequestProcessor {
            whenever(hub.options).thenReturn(options)
            return SentryRequestHttpServletRequestProcessor(SpringMvcTransactionNameProvider(), request)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when event does not have transaction name, sets the transaction name from the current request`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .requestAttr(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/some-path")
            .buildRequest(MockServletContext())
        val eventProcessor = fixture.getSut(request)
        val event = SentryEvent()

        eventProcessor.process(event, Hint())

        assertNotNull(event.transaction)
        assertEquals("GET /some-path", event.transaction)
    }

    @Test
    fun `when event has transaction name set, does not overwrite transaction name with value from the current request`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .requestAttr(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/some-path")
            .buildRequest(MockServletContext())
        val eventProcessor = fixture.getSut(request)
        val event = SentryEvent()
        event.transaction = "some-transaction"

        eventProcessor.process(event, Hint())

        assertNotNull(event.transaction)
        assertEquals("some-transaction", event.transaction)
    }
}
