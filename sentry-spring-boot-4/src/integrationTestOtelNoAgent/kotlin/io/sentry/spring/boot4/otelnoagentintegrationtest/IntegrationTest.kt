package io.sentry.spring.boot4.otelnoagentintegrationtest

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.sentry.test.MockSentryServer
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner

/**
 * Drives [IntegrationTestApplication] over HTTP and asserts what reaches [server]. The app traces
 * through the OpenTelemetry SDK in its own JVM, set up by sentry-opentelemetry-agentless-spring.
 *
 * Subclasses share one application context, one server and one Kafka broker, so each starts once
 * per suite. Envelopes that arrive late can outlive [server]'s reset, so tests match on values only
 * they produce.
 */
@RunWith(SpringRunner::class)
// An in-JVM KRaft broker, so no Docker is needed. As part of the context, it starts before the
// app's Kafka listener connects and stops after it.
@EmbeddedKafka(topics = [KAFKA_TOPIC], bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@SpringBootTest(
  classes = [IntegrationTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties =
    [
      "sentry.traces-sample-rate=1.0",
      "sentry.enable-queue-tracing=true",
      "sentry.logs.enabled=true",
      "sentry.profile-session-sample-rate=1.0",
      "sentry.profile-lifecycle=TRACE",
      "sentry.profiling-traces-dir-path=\${java.io.tmpdir}/sentry-integration-test/profiling-traces",
      "spring.datasource.url=jdbc:p6spy:hsqldb:mem:testdb",
      "spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "otel.propagators=tracecontext,baggage,sentry",
      "otel.traces.exporter=none",
      "otel.metrics.exporter=none",
      "otel.logs.exporter=none",
    ],
)
abstract class IntegrationTest {
  @LocalServerPort private var port = 0

  // The context's TestRestTemplate is instrumented by the OpenTelemetry starter, which would
  // replace the trace headers tests send with its own, so tests use one built outside the context.
  val restTemplate: TestRestTemplate by lazy {
    TestRestTemplate(RestTemplateBuilder().rootUri("http://localhost:$port"))
  }

  @Autowired private lateinit var openTelemetry: OpenTelemetry

  @Before
  fun resetServer() {
    tracerProvider = (openTelemetry as OpenTelemetrySdk).sdkTracerProvider
    server.reset()
  }

  companion object {
    @Volatile private var tracerProvider: SdkTracerProvider? = null

    // Sentry exports OpenTelemetry spans in batches every 5 seconds, so flush while waiting.
    val server = MockSentryServer {
      tracerProvider?.forceFlush()?.join(1, TimeUnit.SECONDS)
    }

    @JvmStatic
    @DynamicPropertySource
    fun sentryDsn(registry: DynamicPropertyRegistry) {
      registry.add("sentry.dsn") { server.dsn }
    }
  }
}
