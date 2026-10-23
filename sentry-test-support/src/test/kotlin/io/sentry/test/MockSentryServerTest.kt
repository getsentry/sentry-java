package io.sentry.test

import com.google.common.truth.Truth.assertThat
import io.sentry.Sentry
import io.sentry.SentryItemType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MockSentryServerTest {
  private val server = MockSentryServer()

  @AfterTest
  fun teardown() {
    Sentry.close()
    server.close()
  }

  @Test
  fun `receives an event sent through the HTTP transport`() {
    initForTest { it.dsn = server.dsn }

    val eventId = Sentry.captureMessage("hello")
    Sentry.flush(5000)

    val envelope = server.awaitEnvelope { it.header.eventId == eventId }
    assertThat(envelope.items.single().header.type).isEqualTo(SentryItemType.Event)
  }

  @Test
  fun `awaitEnvelope fails once the timeout passes without a match`() {
    assertFailsWith<AssertionError> { server.awaitEnvelope(timeoutMillis = 100) { true } }
  }

  @Test
  fun `assertNoEnvelope passes when nothing matches`() {
    initForTest { it.dsn = server.dsn }

    Sentry.captureMessage("hello")
    Sentry.flush(5000)

    server.assertNoEnvelope(millis = 100) { it.header.eventId == null }
  }

  @Test
  fun `assertNoEnvelope fails when a matching envelope arrives`() {
    initForTest { it.dsn = server.dsn }

    val eventId = Sentry.captureMessage("hello")

    assertFailsWith<AssertionError> {
      server.assertNoEnvelope(millis = 5000) { it.header.eventId == eventId }
    }
  }
}
