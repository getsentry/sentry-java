package io.sentry.spring.boot4.log4j2integrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.Sentry
import io.sentry.test.MockSentryServer
import io.sentry.test.event
import io.sentry.test.hasFlag
import kotlin.test.Test
import org.junit.runner.RunWith
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner

/**
 * Runs [IntegrationTestApplication] with Log4j2 instead of Logback, so Sentry's auto-configuration
 * attaches its Log4j2 appender, and asserts what reaches [server].
 */
@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [IntegrationTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  // Sentry only attaches its Log4j2 appender when asked to.
  properties = ["sentry.traces-sample-rate=1.0", "sentry.logging.enabled=true"],
)
class Log4j2IntegrationTest {
  @LocalServerPort private var port = 0

  // The context's TestRestTemplate comes from the RestTemplateBuilder that Sentry customizes, so
  // it would add trace headers of its own to the requests tests send. Tests use a plain one.
  private val restTemplate: TestRestTemplate by lazy {
    TestRestTemplate(RestTemplateBuilder().rootUri("http://localhost:$port"))
  }

  @Test
  fun `error logged through Log4j2 reaches Sentry with the request's flag`() {
    val response = restTemplate.getForEntity("/person/1", String::class.java)
    assertThat(response.statusCode.value()).isEqualTo(500)
    Sentry.flush(5000)

    val logged =
      server.awaitEnvelope { it.event()?.message?.formatted == "Trying person with id=1" }.event()!!
    assertThat(logged.hasFlag("my-feature-flag", true)).isTrue()
    assertThat(logged.sdk!!.integrationSet).contains("Log4j")

    val thrown =
      server
        .awaitEnvelope {
          it.event()?.exceptions?.any { e -> e.value == "Something went wrong [id=1]" } == true
        }
        .event()!!
    assertThat(thrown.hasFlag("my-feature-flag", true)).isTrue()
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
