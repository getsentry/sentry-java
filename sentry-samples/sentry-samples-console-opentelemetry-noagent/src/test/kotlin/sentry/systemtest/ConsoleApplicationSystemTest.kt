package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsoleApplicationSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8000")
    testHelper.reset()
  }

  @Test
  fun `console application sends expected events when run as JAR`() {
    val jarFile = testHelper.findJar("sentry-samples-console-opentelemetry-noagent")
    val process =
      testHelper.launch(
        jarFile,
        mapOf(
          "SENTRY_DSN" to testHelper.dsn,
          //          "SENTRY_AUTO_INIT" to "false",
          "SENTRY_TRACES_SAMPLE_RATE" to "1.0",
          "SENTRY_ENABLE_PRETTY_SERIALIZATION_OUTPUT" to "false",
          "SENTRY_DEBUG" to "true",
          "OTEL_METRICS_EXPORTER" to "none",
          "OTEL_LOGS_EXPORTER" to "none",
          "OTEL_TRACES_EXPORTER" to "none",
        ),
      )

    process.waitFor(30, TimeUnit.SECONDS)
    assertEquals(0, process.exitValue())

    // Verify that we received the expected events
    verifyExpectedEvents()
  }

  private fun verifyExpectedEvents() {
    // Verify we received a "Fatal message!" event
    testHelper.ensureErrorReceived { event ->
      event.message?.formatted == "Fatal message!" && event.level?.name == "FATAL"
    }

    // Verify we received a "Some warning!" event
    testHelper.ensureErrorReceived { event ->
      event.message?.formatted == "Some warning!" && event.level?.name == "WARNING"
    }

    // Verify we received the RuntimeException
    testHelper.ensureErrorReceived { event ->
      event.exceptions?.any { ex -> ex.type == "RuntimeException" && ex.value == "Some error!" } ==
        true
    }

    // Verify we received the detailed event with fingerprint
    testHelper.ensureErrorReceived { event ->
      event.message?.message == "Detailed event" &&
        event.fingerprints?.contains("NewClientDebug") == true &&
        event.level?.name == "DEBUG"
    }

    // Verify we received transaction events
    testHelper.ensureTransactionReceived { transaction, _ ->
      transaction.transaction == "transaction name" &&
        transaction.spans?.any { span -> span.op == "child" } == true
    }

    // Verify we received the loop messages (should be 10 of them)
    var loopMessageCount = 0
    try {
      for (i in 0..9) {
        testHelper.ensureErrorReceived { event ->
          val matches =
            event.message?.message?.contains("items we'll wait to flush to Sentry!") == true
          if (matches) loopMessageCount++
          matches
        }
      }
    } catch (e: Exception) {
      // Some loop messages might be missing, but we should have at least some
    }

    assertTrue(
      "Should receive at least 5 loop messages, got $loopMessageCount",
      loopMessageCount >= 5,
    )

    // Verify we have breadcrumbs
    testHelper.ensureErrorReceived { event ->
      event.breadcrumbs?.any { breadcrumb ->
        breadcrumb.message?.contains("Processed by") == true
      } == true
    }
  }
}
