package io.sentry.spring.boot.jakarta

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.FeatureFlag
import io.sentry.test.ChildJvmApp
import io.sentry.test.MockSentryServer
import io.sentry.test.event
import io.sentry.test.hasFlag
import io.sentry.test.hasLogWithAttribute
import io.sentry.test.hasLogWithBody
import io.sentry.test.hasOp
import io.sentry.test.hasSpanWithOp
import io.sentry.test.logs
import io.sentry.test.transaction
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Runs a Spring Boot app with the built OpenTelemetry agent in a child JVM. Sentry is initialized
 * either by the agent or, with `SENTRY_AUTO_INIT=false`, by the Spring Boot starter.
 */
class OtelAgentPersonTest {
  private val server = MockSentryServer()
  private var app: ChildJvmApp? = null

  @AfterTest
  fun teardown() {
    app?.close()
    server.close()
  }

  @Test
  fun `failed request is reported when the agent initializes Sentry`() {
    assertFailedRequestIsReported(autoInit = true, sdkName = "sentry.java.opentelemetry.agent")
  }

  @Test
  fun `failed request is reported when the Spring Boot starter initializes Sentry`() {
    assertFailedRequestIsReported(autoInit = false, sdkName = "sentry.java.spring-boot.jakarta")
  }

  private fun assertFailedRequestIsReported(autoInit: Boolean, sdkName: String) {
    val port = startApp(autoInit)

    val connection = URL("http://127.0.0.1:$port/person/1").openConnection() as HttpURLConnection
    assertThat(connection.responseCode).isEqualTo(500)

    val logged =
      server.awaitEnvelope { it.event()?.message?.formatted == "Trying person with id=1" }.event()!!
    assertThat(logged.hasFlag("my-feature-flag", true)).isTrue()

    val thrown =
      server
        .awaitEnvelope {
          it.event()?.exceptions?.any { e -> e.value == "Something went wrong [id=1]" } == true
        }
        .event()!!
    assertThat(thrown.hasFlag("my-feature-flag", true)).isTrue()

    // The agent's BatchSpanProcessor exports every 5s.
    val transactionEnvelope =
      server.awaitEnvelope(timeoutMillis = 30_000) {
        it.transaction()?.contexts?.trace?.operation == "http.server"
      }
    assertThat(transactionEnvelope.header.sdkVersion!!.name).isEqualTo(sdkName)
    val transaction = transactionEnvelope.transaction()!!
    assertThat(
        transaction.hasOp(
          "http.server",
          FeatureFlag("flag.evaluation.transaction-feature-flag", true),
        )
      )
      .isTrue()
    assertThat(
        transaction.hasSpanWithOp(
          "spanCreatedThroughOtelApi",
          FeatureFlag("flag.evaluation.my-feature-flag", true),
        )
      )
      .isTrue()
    assertThat(transaction.hasSpanWithOp("spanCreatedThroughSentryApi")).isTrue()

    val logs =
      server
        .awaitEnvelope(timeoutMillis = 30_000) {
          it.logs()?.items?.any { log -> log.body == "warn Sentry logging" } == true
        }
        .logs()!!
    assertThat(logs.hasLogWithBody("error Sentry logging")).isTrue()
    assertThat(logs.hasLogWithBody("hello there world!")).isTrue()
    assertThat(logs.hasLogWithAttribute("warn Sentry logging", "user.type", "admin")).isTrue()
    assertThat(logs.hasLogWithAttribute("warn Sentry logging", "feature.version", 2)).isTrue()
    assertThat(logs.hasLogWithAttribute("warn Sentry logging", "debug.enabled", true)).isTrue()
  }

  private fun startApp(autoInit: Boolean): Int {
    val app =
      ChildJvmApp.start(
        mainClass = "io.sentry.spring.boot.jakarta.agentfixture.AgentFixtureApplicationKt",
        classpath = System.getProperty("sentry.test.appClasspath"),
        jvmArgs = listOf("-javaagent:${System.getProperty("sentry.test.agentJar")}"),
        env =
          mapOf(
            "SENTRY_AUTO_INIT" to autoInit.toString(),
            "SENTRY_DSN" to server.dsn,
            "SENTRY_TRACES_SAMPLE_RATE" to "1.0",
            "SENTRY_LOGS_ENABLED" to "true",
            "OTEL_TRACES_EXPORTER" to "none",
            "OTEL_METRICS_EXPORTER" to "none",
            "OTEL_LOGS_EXPORTER" to "none",
          ),
        args = listOf("--server.port=0"),
      )
    this.app = app
    return app.awaitPort()
  }
}
