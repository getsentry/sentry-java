package io.sentry.spring.boot.otelnoagentintegrationtest

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.sentry.test.MockSentryServer
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner

/**
 * Drives [IntegrationTestApplication] over HTTP and asserts what reaches [server]. The app traces
 * through the OpenTelemetry SDK in its own JVM, set up by sentry-opentelemetry-agentless-spring.
 *
 * Each test class starts its own application context and closes it afterwards, so only one is ever
 * live. Sentry is global: a second live context, like the one KafkaOtelCoexistenceIntegrationTest
 * needs, re-initializes it with that context's OpenTelemetry SDK, and the Sentry API spans of the
 * first context's tests end up in the wrong one.
 *
 * Subclasses share one server. Envelopes that arrive late can outlive [server]'s reset, so tests
 * match on values only they produce.
 */
@RunWith(SpringRunner::class)
@DirtiesContext
@SpringBootTest(
  classes = [IntegrationTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties =
    [
      "sentry.traces-sample-rate=1.0",
      "sentry.logs.enabled=true",
      "sentry.profile-session-sample-rate=1.0",
      "sentry.profile-lifecycle=TRACE",
      "sentry.profiling-traces-dir-path=\${java.io.tmpdir}/sentry-integration-test/profiling-traces",
      // A database per context, because the in-memory one outlives its context and the next
      // context's schema.sql would find its tables.
      "spring.datasource.url=jdbc:p6spy:hsqldb:mem:\${random.uuid}",
      "spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver",
      "otel.propagators=tracecontext,baggage,sentry",
      "otel.traces.exporter=none",
      "otel.metrics.exporter=none",
      "otel.logs.exporter=none",
    ],
)
abstract class IntegrationTest {
  // Rather than @LocalServerPort, which moved packages in Spring Boot 2.7.
  @Value("\${local.server.port}") private var port = 0

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
