package io.sentry.spring.boot4.otelagentintegrationtest

import io.sentry.test.ChildJvmApp
import io.sentry.test.MockSentryServer
import io.sentry.test.requiredTestProperty
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker

/**
 * The agent fixture app, running in a child JVM with the built OpenTelemetry agent attached, and
 * the server it reports to. The app takes seconds to start, so each test JVM starts it once and
 * stops it when the JVM exits.
 *
 * The test task decides what initializes Sentry: the agent or, with `SENTRY_AUTO_INIT=false`, the
 * Spring Boot starter.
 */
object AgentApp {
  val autoInit = requiredTestProperty("sentry.test.agentAutoInit").toBoolean()

  val server = MockSentryServer()

  // An in-JVM KRaft broker with the fixture's KAFKA_TOPIC, so no Docker is needed.
  private val kafka = EmbeddedKafkaKraftBroker(1, 1, "sentry-topic").apply { afterPropertiesSet() }

  private val app =
    ChildJvmApp.start(
      mainClass = "io.sentry.spring.boot4.agentfixture.AgentFixtureApplicationKt",
      classpath = requiredTestProperty("sentry.test.appClasspath"),
      jvmArgs = listOf("-javaagent:${requiredTestProperty("sentry.test.agentJar")}"),
      // Environment variables, because the agent doesn't read Spring Boot's properties.
      env =
        mapOf(
          "SENTRY_AUTO_INIT" to autoInit.toString(),
          "SENTRY_DSN" to server.dsn,
          "SENTRY_TRACES_SAMPLE_RATE" to "1.0",
          "SENTRY_LOGS_ENABLED" to "true",
          "SENTRY_ENABLE_CACHE_TRACING" to "true",
          "SENTRY_ENABLE_QUEUE_TRACING" to "true",
          "SENTRY_PROFILE_SESSION_SAMPLE_RATE" to "1.0",
          "SENTRY_PROFILE_LIFECYCLE" to "TRACE",
          "SENTRY_PROFILING_TRACES_DIR_PATH" to
            "${System.getProperty("java.io.tmpdir")}/sentry-integration-test/profiling-traces",
          "OTEL_TRACES_EXPORTER" to "none",
          "OTEL_METRICS_EXPORTER" to "none",
          "OTEL_LOGS_EXPORTER" to "none",
        ),
      args = listOf("--server.port=0", "--spring.kafka.bootstrap-servers=${kafka.brokersAsString}"),
    )

  init {
    Runtime.getRuntime()
      .addShutdownHook(
        Thread {
          app.close()
          kafka.destroy()
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
