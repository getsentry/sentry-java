package io.sentry.opentelemetry.agent

import com.google.common.truth.Truth.assertThat
import io.sentry.opentelemetry.agent.fixture.HelloServer
import io.sentry.test.MockSentryServer
import io.sentry.test.transaction
import io.sentry.time.Deadline
import io.sentry.time.JavaMonotonicTicker
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Runs an app with the built agent attached, in a child JVM, so the test's own JVM stays
 * uninstrumented. Instrumenting it would turn the test's HTTP calls and [MockSentryServer] itself
 * into spans.
 */
class AgentSmokeTest {
  private val server = MockSentryServer()
  private val workDir = Files.createTempDirectory("agent-smoke-test").toFile()
  private val appLog = File(workDir, "app.log")
  private var app: Process? = null

  @AfterTest
  fun teardown() {
    app?.destroyForcibly()?.waitFor()
    server.close()
    if (appLog.exists()) {
      println(appLog.readText())
    }
    workDir.deleteRecursively()
  }

  @Test
  fun `agent sends a transaction for an incoming request`() {
    val port = launchWithAgent(HelloServer::class.java)

    URL("http://127.0.0.1:$port/hello").readText()

    // The agent's BatchSpanProcessor exports every 5s.
    val envelope =
      server.awaitEnvelope(timeoutMillis = 30_000) {
        it.transaction()?.contexts?.trace?.operation == "http.server"
      }
    assertThat(envelope.header.sdkVersion!!.name).isEqualTo("sentry.java.opentelemetry.agent")
  }

  private fun launchWithAgent(mainClass: Class<*>): Int {
    val portFile = File(workDir, "port")
    val process =
      ProcessBuilder(
          File(System.getProperty("java.home"), "bin/java").path,
          "-javaagent:${System.getProperty("sentry.test.agentJar")}",
          "-cp",
          File(mainClass.protectionDomain.codeSource.location.toURI()).path,
          mainClass.name,
          portFile.path,
        )
        .redirectErrorStream(true)
        .redirectOutput(appLog)
        .apply {
          environment()["SENTRY_DSN"] = server.dsn
          environment()["SENTRY_TRACES_SAMPLE_RATE"] = "1.0"
          environment()["OTEL_TRACES_EXPORTER"] = "none"
          environment()["OTEL_METRICS_EXPORTER"] = "none"
          environment()["OTEL_LOGS_EXPORTER"] = "none"
        }
        .start()
    app = process

    val deadline = Deadline.after(JavaMonotonicTicker.getInstance(), 60, TimeUnit.SECONDS)
    while (true) {
      portFile
        .takeIf { it.exists() }
        ?.readText()
        ?.toIntOrNull()
        ?.let {
          return it
        }
      check(process.isAlive) { "App exited with ${process.exitValue()}" }
      check(!deadline.hasPassed()) { "App did not start within 60s" }
      Thread.sleep(100)
    }
  }
}
