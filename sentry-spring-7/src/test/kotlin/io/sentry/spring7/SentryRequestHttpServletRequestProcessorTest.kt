package io.sentry.spring7

import io.sentry.Hint
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.TransactionContext.DEFAULT_TRANSACTION_NAME
import io.sentry.spring7.tracing.SpringMvcTransactionNameProvider
import io.sentry.spring7.tracing.TransactionNameProvider
import jakarta.servlet.http.HttpServletRequest
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.web.servlet.HandlerMapping

class SentryRequestHttpServletRequestProcessorTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    val logger = mock<ILogger>()

    fun getSut(
      request: HttpServletRequest,
      options: SentryOptions = SentryOptions(),
      provider: TransactionNameProvider = SpringMvcTransactionNameProvider(),
    ): SentryRequestHttpServletRequestProcessor {
      whenever(scopes.options).thenReturn(options)
      return SentryRequestHttpServletRequestProcessor(provider, request, logger)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `when event does not have transaction name, sets the transaction name from the current request`() {
    val request =
      MockMvcRequestBuilders.get(URI.create("http://example.com?param1=xyz"))
        .requestAttr(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/some-path")
        .buildRequest(MockServletContext())
    val eventProcessor = fixture.getSut(request)
    val event = SentryEvent()

    eventProcessor.process(event, Hint())

    assertNotNull(event.transaction)
    assertEquals("GET /some-path", event.transaction)
  }

  @Test
  fun `provider failure uses safe transaction name`() {
    val failure = RuntimeException("provider failed")
    val request = mock<HttpServletRequest>()
    val eventProcessor =
      fixture.getSut(request, provider = TransactionNameProvider { throw failure })
    val event = SentryEvent()

    eventProcessor.process(event, Hint())

    assertThat(event.transaction).isEqualTo(DEFAULT_TRANSACTION_NAME)
  }

  @Test
  fun `fatal provider failure propagates`() {
    val failure = OutOfMemoryError("fatal")
    val request = mock<HttpServletRequest>()
    val eventProcessor =
      fixture.getSut(request, provider = TransactionNameProvider { throw failure })

    assertThat(assertFailsWith<OutOfMemoryError> { eventProcessor.process(SentryEvent(), Hint()) })
      .isSameAs(failure)
  }

  @Test
  fun `when event has transaction name set, does not overwrite transaction name with value from the current request`() {
    val request =
      MockMvcRequestBuilders.get(URI.create("http://example.com?param1=xyz"))
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
