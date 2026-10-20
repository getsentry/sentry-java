package io.sentry.spring.boot4.webfluxintegrationtest

import io.sentry.test.MockSentryServer
import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
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
// Spring Boot 4 only provides TestRestTemplate when asked to.
@AutoConfigureTestRestTemplate
@SpringBootTest(
  classes = [WebfluxIntegrationTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties =
    [
      "sentry.traces-sample-rate=1.0",
      "sentry.logs.enabled=true",
      "sentry.reactive.thread-local-accessor-enabled=true",
      "sentry.profile-session-sample-rate=1.0",
      "sentry.profile-lifecycle=TRACE",
      "sentry.profiling-traces-dir-path=\${java.io.tmpdir}/sentry-integration-test/profiling-traces",
    ],
)
abstract class WebfluxIntegrationTest {
  @Autowired lateinit var restTemplate: TestRestTemplate

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
