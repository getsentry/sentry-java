package io.sentry.logback

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import com.google.common.truth.Truth.assertThat
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.test.MockSentryServer
import io.sentry.test.event
import io.sentry.test.hasFlag
import io.sentry.test.logs
import kotlin.test.AfterTest
import kotlin.test.Test
import org.slf4j.MDC

class SentryAppenderIntegrationTest {
  private val server = MockSentryServer()
  private val loggerContext = LoggerContext()

  @AfterTest
  fun teardown() {
    loggerContext.stop()
    Sentry.close()
    server.close()
    MDC.clear()
  }

  @Test
  fun `appender configured from XML sends events, breadcrumbs and logs`() {
    loggerContext.putProperty("dsn", server.dsn)
    JoranConfigurator()
      .apply { context = loggerContext }
      .doConfigure(javaClass.getResource("/sentry-appender-integration-test.xml"))
    val logger = loggerContext.getLogger(SentryAppenderIntegrationTest::class.java)

    logger.debug("Hello Sentry!")
    MDC.put("userId", "user-1")
    Sentry.setAttribute("user.type", "admin")
    Sentry.setAttribute("feature.version", 2)
    Sentry.setAttribute("debug.enabled", true)
    Sentry.addFeatureFlag("my-feature-flag", true)
    logger.warn("important warning")
    logger.info("User has made a purchase of product: {}", 445)
    logger.error("Something went wrong", RuntimeException("Invalid productId=445"))
    Sentry.flush(5000)

    val warning = server.awaitEnvelope { it.event()?.level == SentryLevel.WARNING }.event()!!
    assertThat(warning.message!!.message).isEqualTo("important warning")
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
