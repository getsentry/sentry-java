package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import kotlin.test.Test

class BuddyExceptionMapperTest {
  @Test
  fun `flattens the primary exception and reverses frames so the crash site is first`() {
    val caller =
      SentryStackFrame().apply {
        module = "io.a.Caller"
        filename = "Caller.kt"
        lineno = 10
        function = "call"
        isInApp = true
      }
    val crash =
      SentryStackFrame().apply {
        module = "io.a.Crash"
        filename = "Crash.kt"
        lineno = 20
        function = "boom"
        isInApp = true
      }
    val exception =
      SentryException().apply {
        type = "IllegalStateException"
        value = "boom"
        stacktrace = SentryStackTrace(listOf(caller, crash))
      }
    val event = SentryEvent().apply { exceptions = listOf(exception) }

    val report = event.toBuddyExceptionReport(7L)!!

    assertThat(report.id).isEqualTo(7L)
    assertThat(report.type).isEqualTo("IllegalStateException")
    assertThat(report.value).isEqualTo("boom")
    assertThat(report.frames.map { it.filename }).containsExactly("Crash.kt", "Caller.kt").inOrder()
    assertThat(report.frames.first().display).isEqualTo("Crash.boom (Crash.kt:20)")
  }

  @Test
  fun `returns null when the event carries no exception`() {
    assertThat(SentryEvent().toBuddyExceptionReport(1L)).isNull()
  }
}
