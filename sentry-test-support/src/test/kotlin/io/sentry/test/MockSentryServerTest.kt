package io.sentry.test

import com.google.common.truth.Truth.assertThat
import io.sentry.Sentry
import io.sentry.SentryItemType
import java.net.HttpURLConnection
import java.net.URI
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
  fun `beforeEachCheck runs while waiting`() {
    var checks = 0
    MockSentryServer { checks++ }
      .use { server ->
        assertFailsWith<AssertionError> { server.awaitEnvelope(timeoutMillis = 200) { true } }
        server.assertNoEnvelope(millis = 200) { true }
      }

    assertThat(checks).isGreaterThan(2)
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

  @Test
  fun `awaitEnvelope fails with the cause when a body is not an envelope`() {
    val url = URI(server.dsn.replace("key@", "")).resolve("/api/0/envelope/").toURL()
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.outputStream.use { it.write("not an envelope".toByteArray()) }
    assertThat(connection.responseCode).isEqualTo(500)

    val error = assertFailsWith<AssertionError> { server.awaitEnvelope { true } }
    assertThat(error).hasCauseThat().isNotNull()
  }
}
