package io.sentry.spring.boot4.webfluxintegrationtest

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
 * Drives [WebfluxIntegrationTestApplication], a reactive app, over HTTP and asserts what reaches
 * [server].
 *
 * Subclasses share one application context and one server, so the app starts once per suite.
 * Envelopes that arrive late can outlive [server]'s reset, so tests match on values only they
 * produce.
 */
@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [WebfluxIntegrationTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties =
    [
      "sentry.traces-sample-rate=1.0",
      "sentry.enable-cache-tracing=true",
      "sentry.logs.enabled=true",
      "sentry.reactive.thread-local-accessor-enabled=true",
      "sentry.profile-session-sample-rate=1.0",
      "sentry.profile-lifecycle=TRACE",
      "sentry.profiling-traces-dir-path=\${java.io.tmpdir}/sentry-integration-test/profiling-traces",
    ],
)
abstract class WebfluxIntegrationTest {
  @LocalServerPort private var port = 0

  // The context's TestRestTemplate comes from the RestTemplateBuilder that Sentry customizes, so
  // it would add trace headers of its own to the requests tests send. Tests use a plain one.
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
