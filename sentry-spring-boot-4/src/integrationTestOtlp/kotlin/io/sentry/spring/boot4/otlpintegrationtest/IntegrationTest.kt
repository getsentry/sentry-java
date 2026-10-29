package io.sentry.spring.boot4.otlpintegrationtest

import io.sentry.test.MockSentryServer
import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner

/**
 * Drives [IntegrationTestApplication] over HTTP and asserts what reaches [server]. The app has the
 * OpenTelemetry Spring Boot starter, which would export its spans over OTLP, and Sentry handles the
 * rest, as set up by sentry-opentelemetry-otlp-spring.
 *
 * Subclasses share one application context and one server, so the app starts once per suite.
 * Envelopes that arrive late can outlive [server]'s reset, so tests match on values only they
 * produce.
 */
@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [IntegrationTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties =
    [
      // Sentry also traces, as the sample did in CI with SENTRY_TRACES_SAMPLE_RATE, so the tests
      // can check its transactions next to OpenTelemetry.
      "sentry.traces-sample-rate=1.0",
      "sentry.enable-cache-tracing=true",
      "sentry.logs.enabled=true",
      "sentry.profile-session-sample-rate=1.0",
      "sentry.profile-lifecycle=TRACE",
      "sentry.profiling-traces-dir-path=\${java.io.tmpdir}/sentry-integration-test/profiling-traces",
      "spring.datasource.url=jdbc:p6spy:hsqldb:mem:testdb",
      "spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver",
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

  @Before
  fun resetServer() {
    server.reset()
  }

  companion object {
    val server = MockSentryServer()

    @JvmStatic
    @DynamicPropertySource
    fun sentryDsn(registry: DynamicPropertyRegistry) {
      registry.add("sentry.dsn") { server.dsn }
    }
  }
}
