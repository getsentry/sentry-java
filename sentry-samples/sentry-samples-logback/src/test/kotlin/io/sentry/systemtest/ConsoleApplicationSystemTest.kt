package io.sentry.systemtest.io.sentry.systemtest

import io.sentry.SentryLevel
import io.sentry.systemtest.util.TestHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ConsoleApplicationSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8000")
    testHelper.reset()
  }

  @Test
  fun `logback application sends expected events when run as JAR`() {
    val jarFile = testHelper.findJar("sentry-samples-logback")
    val process =
      testHelper.launch(
        jarFile,
        mapOf(
          "SENTRY_DSN" to testHelper.dsn,
          "SENTRY_TRACES_SAMPLE_RATE" to "1.0",
          "SENTRY_ENABLE_PRETTY_SERIALIZATION_OUTPUT" to "false",
          "SENTRY_DEBUG" to "true",
        ),
      )

    process.waitFor(30, TimeUnit.SECONDS)
      Assert.assertEquals(0, process.exitValue())

    // Verify that we received the expected events
    verifyExpectedEvents()
  }

  private fun verifyExpectedEvents() {
    // Verify we received the RuntimeException
    testHelper.ensureErrorReceived { event ->
      event.exceptions?.any { ex -> ex.type == "RuntimeException" && ex.value == "Invalid productId=445" } ==
        true &&
        event.message?.formatted == "Something went wrong" &&
        event.level?.name == "ERROR"
    }

    testHelper.ensureErrorReceived { event ->
      event.breadcrumbs?.firstOrNull { it.message == "Hello Sentry!" && it.level == SentryLevel.DEBUG } != null
    }

    testHelper.ensureErrorReceived { event ->
      event.breadcrumbs?.firstOrNull { it.message == "User has made a purchase of product: 445" && it.level == SentryLevel.INFO } != null
    }

    testHelper.ensureLogsReceived { logs, _ ->
      testHelper.doesContainLogWithBody(logs, "User has made a purchase of product: 445") &&
        testHelper.doesContainLogWithBody(logs, "Something went wrong")
    }
  }
}
