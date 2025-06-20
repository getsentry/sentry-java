package io.sentry.android.core

import android.os.FileObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IEnvelopeSender
import io.sentry.ILogger
import io.sentry.hints.ApplyScopeData
import io.sentry.hints.Resettable
import io.sentry.hints.Retryable
import io.sentry.hints.SubmissionResult
import io.sentry.util.HintUtils
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class EnvelopeFileObserverTest {
  private class Fixture {
    val fileName = "file-name.txt"
    var path: String? = "."
    val envelopeSender = mock<IEnvelopeSender>()
    val logger = mock<ILogger>()

    fun getSut(flushTimeoutMillis: Long): EnvelopeFileObserver =
      EnvelopeFileObserver(path, envelopeSender, logger, flushTimeoutMillis)
  }

  private val fixture = Fixture()

  @Test
  fun `envelope sender is called with fully qualified path`() {
    triggerEvent()

    verify(fixture.envelopeSender)
      .processEnvelopeFile(eq(fixture.path + File.separator + fixture.fileName), any())
  }

  @Test
  fun `when event type is not close write, envelope sender is not called`() {
    triggerEvent(eventType = FileObserver.CLOSE_WRITE.inv())

    verify(fixture.envelopeSender, never()).processEnvelopeFile(any(), anyOrNull())
  }

  @Test
  fun `when event is fired with null path, envelope reader is not called`() {
    triggerEvent(relativePath = null)

    verify(fixture.envelopeSender, never()).processEnvelopeFile(anyOrNull(), any())
  }

  @Test
  fun `when null is passed as a path, ctor throws`() {
    fixture.path = null

    // since EnvelopeFileObserver extends FileObserver and FileObserver requires a File(path),
    // it throws NullPointerException instead of our own IllegalArgumentException
    assertFailsWith<NullPointerException> { fixture.getSut(0) }
  }

  @Test
  fun `envelope sender is called with fully qualified path and ApplyScopeData hint`() {
    triggerEvent()

    verify(fixture.envelopeSender)
      .processEnvelopeFile(
        eq(fixture.path + File.separator + fixture.fileName),
        check { HintUtils.hasType(it, ApplyScopeData::class.java) },
      )
  }

  @Test
  fun `envelope sender Hint is Resettable`() {
    triggerEvent()

    verify(fixture.envelopeSender)
      .processEnvelopeFile(
        eq(fixture.path + File.separator + fixture.fileName),
        check { HintUtils.hasType(it, Resettable::class.java) },
      )
  }

  @Test
  fun `Hint resets its state`() {
    triggerEvent(flushTimeoutMillis = 0)

    verify(fixture.envelopeSender)
      .processEnvelopeFile(
        eq(fixture.path + File.separator + fixture.fileName),
        check { hints ->
          HintUtils.runIfHasType(hints, SubmissionResult::class.java) { it.setResult(true) }
          HintUtils.runIfHasType(hints, Retryable::class.java) { it.isRetry = true }

          HintUtils.runIfHasType(hints, Resettable::class.java) { it.reset() }

          assertFalse((HintUtils.getSentrySdkHint(hints) as Retryable).isRetry)
          assertFalse((HintUtils.getSentrySdkHint(hints) as SubmissionResult).isSuccess)
        },
      )
  }

  private fun triggerEvent(
    flushTimeoutMillis: Long = 15_000,
    eventType: Int = FileObserver.CLOSE_WRITE,
    relativePath: String? = fixture.fileName,
  ) {
    val sut = fixture.getSut(flushTimeoutMillis)
    sut.onEvent(eventType, relativePath)
  }
}
