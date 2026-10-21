package io.sentry.spring.boot.jakarta.otelagentintegrationtest

import io.sentry.test.ChildJvmApp
import io.sentry.test.MockSentryServer
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder

/**
 * The agent fixture app, running in a child JVM with the built OpenTelemetry agent attached, and
 * the server it reports to. The app takes seconds to start, so each test JVM starts it once and
 * stops it when the JVM exits.
 *
 * The test task decides what initializes Sentry: the agent or, with `SENTRY_AUTO_INIT=false`, the
 * Spring Boot starter.
 */
object AgentApp {
  val autoInit = System.getProperty("sentry.test.agentAutoInit").toBoolean()

  val server = MockSentryServer()

  private val app =
    ChildJvmApp.start(
      mainClass = "io.sentry.spring.boot.jakarta.agentfixture.AgentFixtureApplicationKt",
      classpath = System.getProperty("sentry.test.appClasspath"),
      jvmArgs = listOf("-javaagent:${System.getProperty("sentry.test.agentJar")}"),
      // Environment variables, because the agent doesn't read Spring Boot's properties.
      env =
        mapOf(
          "SENTRY_AUTO_INIT" to autoInit.toString(),
          "SENTRY_DSN" to server.dsn,
          "SENTRY_TRACES_SAMPLE_RATE" to "1.0",
          "SENTRY_LOGS_ENABLED" to "true",
          "SENTRY_PROFILE_SESSION_SAMPLE_RATE" to "1.0",
          "SENTRY_PROFILE_LIFECYCLE" to "TRACE",
          "SENTRY_PROFILING_TRACES_DIR_PATH" to
            "${System.getProperty("java.io.tmpdir")}/sentry-integration-test/profiling-traces",
          "OTEL_TRACES_EXPORTER" to "none",
          "OTEL_METRICS_EXPORTER" to "none",
          "OTEL_LOGS_EXPORTER" to "none",
        ),
      args = listOf("--server.port=0"),
    )

  init {
    Runtime.getRuntime()
      .addShutdownHook(
        Thread {
          app.close()
          server.close()
        }
      )
  }

  // The agent instruments classes as they load, which on a busy CI runner has taken over a minute.
  val restTemplate =
    TestRestTemplate(
      RestTemplateBuilder().rootUri("http://localhost:${app.awaitPort(timeoutSeconds = 180)}")
    )
}
