package io.sentry.spring.boot.otelagentintegrationtest

import io.sentry.test.ChildJvmApp
import io.sentry.test.MockSentryServer
import io.sentry.test.requiredTestProperty
import java.io.Closeable
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder

/**
 * The agent fixture app, running in a child JVM with the built OpenTelemetry agent attached, and
 * the server it reports to. [args] go to the app.
 *
 * The test task decides what initializes Sentry: the agent or, with `SENTRY_AUTO_INIT=false`, the
 * Spring Boot starter.
 */
class AgentApp(args: List<String> = emptyList()) : Closeable {
  val server = MockSentryServer()

  private val app =
    ChildJvmApp.start(
      mainClass = "io.sentry.spring.boot.agentfixture.AgentFixtureApplicationKt",
      classpath = requiredTestProperty("sentry.test.appClasspath"),
      jvmArgs = listOf("-javaagent:${requiredTestProperty("sentry.test.agentJar")}"),
      // Environment variables, because the agent doesn't read Spring Boot's properties.
      env =
        mapOf(
          "SENTRY_AUTO_INIT" to autoInit.toString(),
          "SENTRY_DSN" to server.dsn,
          "SENTRY_TRACES_SAMPLE_RATE" to "1.0",
          "SENTRY_LOGS_ENABLED" to "true",
          "SENTRY_ENABLE_QUEUE_TRACING" to "true",
          "SENTRY_PROFILE_SESSION_SAMPLE_RATE" to "1.0",
          "SENTRY_PROFILE_LIFECYCLE" to "TRACE",
          "SENTRY_PROFILING_TRACES_DIR_PATH" to
            "${System.getProperty("java.io.tmpdir")}/sentry-integration-test/profiling-traces",
          "OTEL_TRACES_EXPORTER" to "none",
          "OTEL_METRICS_EXPORTER" to "none",
          "OTEL_LOGS_EXPORTER" to "none",
        ),
      args = listOf("--server.port=0") + args,
    )

  // The agent instruments classes as they load, which on a busy CI runner has taken over a minute.
  private val port =
    try {
      app.awaitPort(timeoutSeconds = 180)
    } catch (e: IllegalStateException) {
      // The caller never gets this instance, so it can't stop the app.
      close()
      throw e
    }

  val restTemplate = TestRestTemplate(RestTemplateBuilder().rootUri("http://localhost:$port"))

  override fun close() {
    app.close()
    server.close()
  }

  companion object {
    val autoInit = requiredTestProperty("sentry.test.agentAutoInit").toBoolean()

    /**
     * The app most test classes share. It takes seconds to start, so each test JVM starts it once
     * and stops it when the JVM exits. If it doesn't start, the remaining tests fail with the same
     * error rather than each waiting for a new app to time out.
     */
    val shared: AgentApp
      get() = sharedApp.getOrThrow()

    private val sharedApp: Result<AgentApp> by lazy {
      try {
        Result.success(
          AgentApp().also { app -> Runtime.getRuntime().addShutdownHook(Thread { app.close() }) }
        )
      } catch (e: IllegalStateException) {
        Result.failure(e)
      }
    }
  }
}
