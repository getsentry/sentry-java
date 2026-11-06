package io.sentry.jul

import com.google.common.truth.Truth.assertThat
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.test.MockSentryServer
import io.sentry.test.event
import io.sentry.test.hasFlag
import io.sentry.test.logs
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.Test

class SentryHandlerIntegrationTest {
  private val server = MockSentryServer()

  @AfterTest
  fun teardown() {
    // Back to the configuration the other tests read, which also closes this test's handler.
    LogManager.getLogManager().readConfiguration()
    Sentry.close()
    server.close()
    System.clearProperty("sentry.dsn")
  }

  @Test
  fun `handler configured from logging properties sends events, breadcrumbs and logs`() {
    // The handler reads the DSN as external configuration, as an app's sentry.properties would
    // set it. The module's test sentry.properties enables logs.
    System.setProperty("sentry.dsn", server.dsn)
    javaClass.getResourceAsStream("/sentry-handler-integration-test.properties")!!.use {
      LogManager.getLogManager().readConfiguration(it)
    }
    // Outside io.sentry, whose loggers the handler drops so it doesn't capture the SDK's own logs.
    val logger = Logger.getLogger("com.example.App")

    logger.config("Hello Sentry!")
    Sentry.setAttribute("user.type", "admin")
    Sentry.setAttribute("feature.version", 2)
    Sentry.setAttribute("debug.enabled", true)
    Sentry.addFeatureFlag("my-feature-flag", true)
    logger.warning("important warning")
    logger.log(Level.INFO, "User has made a purchase of product: %d", 445)
    logger.log(Level.SEVERE, "Something went wrong", RuntimeException("Invalid productId=445"))
    Sentry.flush(5000)

    val warning = server.awaitEnvelope { it.event()?.level == SentryLevel.WARNING }.event()!!
    assertThat(warning.message!!.message).isEqualTo("important warning")
    assertThat(warning.hasFlag("my-feature-flag", true)).isTrue()

    val error = server.awaitEnvelope { it.event()?.level == SentryLevel.ERROR }.event()!!
    assertThat(error.message!!.message).isEqualTo("Something went wrong")
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
