package io.sentry.opentelemetry.agent

import com.google.common.truth.Truth.assertThat
import io.sentry.opentelemetry.agent.fixture.HelloServer
import io.sentry.test.ChildJvmApp
import io.sentry.test.MockSentryServer
import io.sentry.test.requiredTestProperty
import io.sentry.test.transaction
import java.io.File
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Runs an app with the built agent attached, in a child JVM, so the test's own JVM stays
 * uninstrumented. Instrumenting it would turn the test's HTTP calls and [MockSentryServer] itself
 * into spans.
 */
class AgentSmokeTest {
  private val server = MockSentryServer()
  private var app: ChildJvmApp? = null

  @AfterTest
  fun teardown() {
    app?.close()
    server.close()
  }

  @Test
  fun `agent sends a transaction for an incoming request`() {
    val app =
      ChildJvmApp.start(
          mainClass = HelloServer::class.java.name,
          classpath =
            File(HelloServer::class.java.protectionDomain.codeSource.location.toURI()).path,
          jvmArgs = listOf("-javaagent:${requiredTestProperty("sentry.test.agentJar")}"),
          env =
            mapOf(
              "SENTRY_DSN" to server.dsn,
              "SENTRY_TRACES_SAMPLE_RATE" to "1.0",
              "OTEL_TRACES_EXPORTER" to "none",
              "OTEL_METRICS_EXPORTER" to "none",
              "OTEL_LOGS_EXPORTER" to "none",
            ),
        )
        .also { app = it }

    URL("http://127.0.0.1:${app.awaitPort()}/hello").readText()

    // The agent's BatchSpanProcessor exports every 5s.
    val envelope =
      server.awaitEnvelope(timeoutMillis = 30_000) {
        it.transaction()?.contexts?.trace?.operation == "http.server"
      }
    assertThat(envelope.header.sdkVersion!!.name).isEqualTo("sentry.java.opentelemetry.agent")
  }
}
