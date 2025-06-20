package io.sentry

import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

data class TracingEnabledTestData(
  val tracesSampleRate: Double?,
  val tracesSamplerPresent: Boolean,
  val isTracingEnabled: Boolean,
)

/** Test @link{SentryOptions#isTracingEnabled()} with combination of other options. */
@RunWith(Parameterized::class)
class SentryOptionsTracingTest(private val testData: TracingEnabledTestData) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Collection<Array<Any>> =
      listOf(
          TracingEnabledTestData(null, false, false),
          TracingEnabledTestData(1.0, false, true),
          TracingEnabledTestData(0.0, false, true),
          TracingEnabledTestData(null, true, true),
          TracingEnabledTestData(1.0, true, true),
          TracingEnabledTestData(0.0, true, true),
        )
        .map { arrayOf(it) }
  }

  @Test
  fun `test isTracingEnabled`() {
    val options =
      SentryOptions().apply {
        testData.tracesSampleRate?.let { this.tracesSampleRate = it }
        if (testData.tracesSamplerPresent) {
          this.tracesSampler = SentryOptions.TracesSamplerCallback { samplingContext -> 1.0 }
        }
      }

    assertEquals(
      testData.isTracingEnabled,
      options.isTracingEnabled,
      "combination failed: $testData",
    )
  }
}
