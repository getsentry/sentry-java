package io.sentry.log4j2

import com.google.common.truth.Truth.assertThat
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.test.MockSentryServer
import io.sentry.test.event
import io.sentry.test.hasFlag
import io.sentry.test.logs
import kotlin.test.AfterTest
import kotlin.test.Test
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.core.LoggerContext

class SentryAppenderIntegrationTest {
  private val server = MockSentryServer()

  // A context of its own, so the other tests' context keeps its configuration.
  private val loggerContext =
    LoggerContext(
      "sentry-appender-integration-test",
      null,
      javaClass.getResource("/sentry-appender-integration-test.xml")!!.toURI(),
    )

  @AfterTest
  fun teardown() {
    loggerContext.stop()
    Sentry.close()
    server.close()
    ThreadContext.clearAll()
    System.clearProperty("integrationTestDsn")
  }

  @Test
  fun `appender configured from XML sends events, breadcrumbs and logs`() {
    System.setProperty("integrationTestDsn", server.dsn)
    loggerContext.start()
    val logger = loggerContext.getLogger(SentryAppenderIntegrationTest::class.java.name)

    logger.debug("Hello Sentry!")
    ThreadContext.put("userId", "user-1")
    Sentry.setAttribute("user.type", "admin")
    Sentry.setAttribute("feature.version", 2)
    Sentry.setAttribute("debug.enabled", true)
    Sentry.addFeatureFlag("my-feature-flag", true)
    logger.info("User has made a purchase of product: {}", 445)
    logger.warn("Important warning")
    logger.error("Something went wrong", RuntimeException("Invalid productId=445"))
    Sentry.flush(5000)

    val warning = server.awaitEnvelope { it.event()?.level == SentryLevel.WARNING }.event()!!
    assertThat(warning.message!!.message).isEqualTo("Important warning")
    assertThat(warning.tags).containsEntry("userId", "user-1")
    assertThat(warning.hasFlag("my-feature-flag", true)).isTrue()

    val error = server.awaitEnvelope { it.event()?.level == SentryLevel.ERROR }.event()!!
    assertThat(error.message!!.formatted).isEqualTo("Something went wrong")
    assertThat(error.exceptions!!.map { it.type to it.value })
      .contains("RuntimeException" to "Invalid productId=445")
    assertThat(error.breadcrumbs!!.map { it.message to it.level })
      .containsAtLeast(
        "Hello Sentry!" to SentryLevel.DEBUG,
        "User has made a purchase of product: 445" to SentryLevel.INFO,
      )

    val logs = server.awaitEnvelope { it.logs() != null }.logs()!!
    assertThat(logs.items.map { it.body }).contains("User has made a purchase of product: 445")
    val failureLog = logs.items.single { it.body == "Something went wrong" }
    assertThat(failureLog.attributes!!.mapValues { it.value.value })
      .containsAtLeast("user.type", "admin", "feature.version", 2, "debug.enabled", true)
  }
}
