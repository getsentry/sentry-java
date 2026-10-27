package io.sentry

import com.google.common.truth.Truth.assertThat
import io.sentry.test.ChildJvmApp
import io.sentry.test.MockSentryServer
import io.sentry.test.event
import io.sentry.test.hasLogWithBody
import io.sentry.test.hasMetric
import io.sentry.test.logs
import io.sentry.test.metrics
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Runs an app in its own JVM that returns from `main` without flushing or closing Sentry, as most
 * apps do, so only the shutdown hook can deliver what it reported.
 */
class ShutdownHookEndToEndTest {
  private val server = MockSentryServer()

  @AfterTest
  fun teardown() {
    server.close()
  }

  @Test
  fun `the shutdown hook delivers what an app reported before it exited`() {
    ChildJvmApp.start(
        mainClass = ExitWithoutFlushApp::class.java.name,
        classpath = System.getProperty("java.class.path"),
        args = listOf(server.dsn),
      )
      .use { app ->
        // A timeout here means a thread the SDK started kept the JVM from exiting.
        assertThat(app.awaitExit()).isEqualTo(0)
      }

    server.awaitEnvelope { it.event()?.message?.formatted == "Reported before exit" }
    server.awaitEnvelope { it.logs()?.hasLogWithBody("Logged before exit") == true }
    server.awaitEnvelope { it.metrics()?.hasMetric("exitMetric", "counter", 1.0) == true }
  }
}

/** Reports an event, a log and a metric, then returns without flushing or closing Sentry. */
object ExitWithoutFlushApp {
  @JvmStatic
  fun main(args: Array<String>) {
    Sentry.init { it.dsn = args[0] }
    Sentry.captureMessage("Reported before exit")
    Sentry.logger().info("Logged before exit")
    Sentry.metrics().count("exitMetric")
  }
}
