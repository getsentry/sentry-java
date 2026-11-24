package io.sentry.android.core.anr

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryExceptionFactory
import io.sentry.SentryOptions
import io.sentry.SentryStackTraceFactory
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import kotlin.test.Test

class AnrCulpritEventHelperTest {

  private val helper =
    AnrCulpritEventHelper(SentryExceptionFactory(SentryStackTraceFactory(SentryOptions())))

  @Test
  fun `creates exception with class and method of the top frame as message`() {
    val stack =
      arrayOf(
        StackTraceElement("com.example.Foo", "bar", "Foo.java", 12),
        StackTraceElement("android.os.Looper", "loop", "Looper.java", 200),
      )
    val culprit = AggregatedStackTrace(stack, 0, stack.size - 1, 1000L, 0.5f)

    val exceptions = helper.toSentryExceptions(culprit, Mechanism().apply { type = "ANR" })

    assertThat(exceptions).isNotNull()
    assertThat(exceptions!!).hasSize(1)
    val exception = exceptions[0]
    assertThat(exception.type).isEqualTo("ApplicationNotResponding")
    assertThat(exception.module).isEqualTo("io.sentry.android.core")
    assertThat(exception.value).isEqualTo("com.example.Foo.bar")
    assertThat(exception.mechanism!!.type).isEqualTo("ANR")
    assertThat(exception.stacktrace!!.frames!!.map { it.function }).containsExactly("loop", "bar")
  }

  @Test
  fun `returns null for an empty culprit stack`() {
    val culprit = AggregatedStackTrace(emptyArray(), 0, -1, 1000L, 0f)

    assertThat(helper.toSentryExceptions(culprit, Mechanism())).isNull()
  }

  @Test
  fun `hasOnlySystemFrames is true for java and android frames only`() {
    val exceptions = exceptionsWithFrames(frame("java.lang.Object"), frame("android.os.Looper"))

    assertThat(AnrCulpritEventHelper.hasOnlySystemFrames(exceptions)).isTrue()
  }

  @Test
  fun `hasOnlySystemFrames is false if one frame is in app`() {
    val exceptions =
      exceptionsWithFrames(frame("android.os.Looper"), frame("android.os.Handler", inApp = true))

    assertThat(AnrCulpritEventHelper.hasOnlySystemFrames(exceptions)).isFalse()
  }

  @Test
  fun `hasOnlySystemFrames is false if one frame is not a system frame`() {
    val exceptions = exceptionsWithFrames(frame("android.os.Looper"), frame("com.example.Foo"))

    assertThat(AnrCulpritEventHelper.hasOnlySystemFrames(exceptions)).isFalse()
  }

  @Test
  fun `hasOnlySystemFrames is false without exceptions`() {
    assertThat(AnrCulpritEventHelper.hasOnlySystemFrames(null)).isFalse()
    assertThat(AnrCulpritEventHelper.hasOnlySystemFrames(emptyList())).isFalse()
  }

  private fun frame(module: String, inApp: Boolean? = null) =
    SentryStackFrame().apply {
      this.module = module
      this.isInApp = inApp
    }

  private fun exceptionsWithFrames(vararg frames: SentryStackFrame) =
    listOf(SentryException().apply { stacktrace = SentryStackTrace(frames.toList()) })
}
