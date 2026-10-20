package io.sentry.spring.boot4.integrationtest

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
 * Drives [IntegrationTestApplication] over HTTP and asserts what reaches [server].
 *
 * Subclasses share one application context and one server, so the app starts once per suite.
 * Envelopes that arrive late can outlive [server]'s reset, so tests match on values only they
 * produce.
 */
@RunWith(SpringRunner::class)
// Spring Boot 4 only provides TestRestTemplate when asked to.
@AutoConfigureTestRestTemplate
@SpringBootTest(
  classes = [IntegrationTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties =
    [
      "sentry.traces-sample-rate=1.0",
      "sentry.clients.ok-http-enabled=true",
      "sentry.enable-cache-tracing=true",
      "sentry.logs.enabled=true",
      "sentry.profile-session-sample-rate=1.0",
      "sentry.profile-lifecycle=TRACE",
      "sentry.profiling-traces-dir-path=\${java.io.tmpdir}/sentry-integration-test/profiling-traces",
      "spring.datasource.url=jdbc:p6spy:hsqldb:mem:testdb",
      "spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver",
    ],
)
abstract class IntegrationTest {
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
