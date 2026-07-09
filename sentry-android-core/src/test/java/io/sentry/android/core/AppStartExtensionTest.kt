package io.sentry.android.core

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.SentryLongDate
import io.sentry.SentryNanotimeDate
import io.sentry.SpanStatus
import io.sentry.android.core.performance.AppStartMetrics
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.N])
class AppStartExtensionTest {

  private val metrics = mock<AppStartMetrics>()

  private fun extension(windowOpen: Boolean = true): AppStartExtension {
    whenever(metrics.canExtendAppStart()).thenReturn(windowOpen)
    return AppStartExtension(metrics)
  }

  /** Simulates the integration's listener: hands a transaction + span back to the extension. */
  private fun AppStartExtension.registerHandOver(
    txn: ITransaction = mock(),
    span: ISpan = mock(),
  ): Pair<ITransaction, ISpan> {
    setExtendAppStartListener { AppStartExtension.ExtendedAppStart(txn, span) }
    return txn to span
  }

  @Test
  fun `extendAppStart fires the listener when the window is open`() {
    val ext = extension(windowOpen = true)
    val calls = AtomicInteger()
    ext.setExtendAppStartListener {
      calls.incrementAndGet()
      null
    }
    ext.extendAppStart()
    assertEquals(1, calls.get())
  }

  @Test
  fun `extendAppStart does not fire the listener when the window is closed`() {
    val ext = extension(windowOpen = false)
    val calls = AtomicInteger()
    ext.setExtendAppStartListener {
      calls.incrementAndGet()
      null
    }
    ext.extendAppStart()
    assertEquals(0, calls.get())
  }

  @Test
  fun `extendAppStart is inert when no listener is registered`() {
    val ext = extension(windowOpen = true)
    ext.extendAppStart()
    assertNull(ext.extendedAppStartSpan)
    assertFalse(ext.isActive)
  }

  @Test
  fun `extendAppStart is ignored when already extending`() {
    val ext = extension(windowOpen = true)
    val calls = AtomicInteger()
    val txn = mock<ITransaction>()
    val span = mock<ISpan>()
    ext.setExtendAppStartListener {
      calls.incrementAndGet()
      AppStartExtension.ExtendedAppStart(txn, span)
    }
    ext.extendAppStart()
    ext.extendAppStart()
    assertEquals(1, calls.get())
  }

  @Test
  fun `getExtendedAppStartSpan returns null when no extension is active`() {
    assertNull(extension().extendedAppStartSpan)
  }

  @Test
  fun `getExtendedAppStartSpan returns the span while extending`() {
    val ext = extension(windowOpen = true)
    val (_, span) = ext.registerHandOver()
    ext.extendAppStart()
    assertSame(span, ext.extendedAppStartSpan)
  }

  @Test
  fun `finishExtendedAppStart finishes the extended span`() {
    val ext = extension(windowOpen = true)
    val (_, span) = ext.registerHandOver()
    ext.extendAppStart()
    ext.finishExtendedAppStart()
    verify(span).finish(SpanStatus.OK)
  }

  @Test
  fun `finishExtendedAppStart does not finish an already finished span`() {
    val ext = extension(windowOpen = true)
    val span = mock<ISpan>()
    whenever(span.isFinished).thenReturn(true)
    ext.registerHandOver(span = span)
    ext.extendAppStart()
    ext.finishExtendedAppStart()
    verify(span, never()).finish(any<SpanStatus>())
  }

  @Test
  fun `isActive reflects the transaction state`() {
    val ext = extension(windowOpen = true)
    assertFalse(ext.isActive)
    val (txn, _) = ext.registerHandOver()
    ext.extendAppStart()
    assertTrue(ext.isActive)
    whenever(txn.isFinished).thenReturn(true)
    assertFalse(ext.isActive)
  }

  @Test
  fun `isExtended stays true once extended, even after the transaction finishes`() {
    val ext = extension(windowOpen = true)
    assertFalse(ext.isExtended)
    val (txn, _) = ext.registerHandOver()
    ext.extendAppStart()
    assertTrue(ext.isExtended)
    whenever(txn.isFinished).thenReturn(true)
    assertFalse(ext.isActive)
    assertTrue(ext.isExtended)
  }

  @Test
  fun `finishTransaction finishes the transaction at the given timestamp`() {
    val ext = extension(windowOpen = true)
    val (txn, _) = ext.registerHandOver()
    ext.extendAppStart()
    val endTimestamp = SentryNanotimeDate()
    ext.finishTransaction(endTimestamp)
    verify(txn).finish(SpanStatus.OK, endTimestamp)
  }

  @Test
  fun `finishTransaction does not finish an already finished transaction`() {
    val ext = extension(windowOpen = true)
    val txn = mock<ITransaction>()
    whenever(txn.isFinished).thenReturn(true)
    ext.registerHandOver(txn = txn)
    ext.extendAppStart()
    ext.finishTransaction(SentryNanotimeDate())
    verify(txn, never()).finish(any<SpanStatus>(), any())
  }

  @Test
  fun `finishTransaction ends at the extended span end when it finished after the given timestamp`() {
    // Headless: the extended span can finish (in onCreate) before finishTransaction runs (at idle)
    // with a finish date later than the headless end. The transaction must end there so it contains
    // the extended span and its duration matches the app start vital.
    val ext = extension(windowOpen = true)
    val txn = mock<ITransaction>()
    val span = mock<ISpan>()
    val spanEnd = SentryLongDate(2_000_000_000L)
    whenever(span.finishDate).thenReturn(spanEnd)
    ext.registerHandOver(txn = txn, span = span)
    ext.extendAppStart()
    ext.finishTransaction(SentryLongDate(1_000_000_000L))
    verify(txn).finish(SpanStatus.OK, spanEnd)
  }

  @Test
  fun `getExtendedEndTime is null while the span is unfinished`() {
    val ext = extension(windowOpen = true)
    ext.registerHandOver()
    ext.extendAppStart()
    assertNull(ext.extendedEndTime)
  }

  @Test
  fun `getExtendedEndTime is null when the extension finished via deadline`() {
    val ext = extension(windowOpen = true)
    val span = mock<ISpan>()
    whenever(span.isFinished).thenReturn(true)
    whenever(span.status).thenReturn(SpanStatus.DEADLINE_EXCEEDED)
    whenever(span.finishDate).thenReturn(SentryNanotimeDate())
    ext.registerHandOver(span = span)
    ext.extendAppStart()
    assertNull(ext.extendedEndTime)
  }

  @Test
  fun `getExtendedEndTime returns the finish date on a user finish`() {
    val ext = extension(windowOpen = true)
    val finishDate = SentryNanotimeDate()
    val span = mock<ISpan>()
    whenever(span.isFinished).thenReturn(true)
    whenever(span.status).thenReturn(SpanStatus.OK)
    whenever(span.finishDate).thenReturn(finishDate)
    ext.registerHandOver(span = span)
    ext.extendAppStart()
    assertSame(finishDate, ext.extendedEndTime)
  }

  @Test
  fun `getExtendedEndTime returns the finish date even when the span still reports unfinished`() {
    // Reproduces the waitForChildren reentrancy: finishing the extended span completes the
    // transaction and runs the event processor before the span's isFinished() flips, while the
    // finish timestamp is already set. getExtendedEndTime() must read the finish date, not the
    // flag.
    val ext = extension(windowOpen = true)
    val finishDate = SentryNanotimeDate()
    val span = mock<ISpan>()
    whenever(span.isFinished).thenReturn(false)
    whenever(span.status).thenReturn(SpanStatus.OK)
    whenever(span.finishDate).thenReturn(finishDate)
    ext.registerHandOver(span = span)
    ext.extendAppStart()
    assertSame(finishDate, ext.extendedEndTime)
  }

  @Test
  fun `clear clears the extension state`() {
    val ext = extension(windowOpen = true)
    ext.registerHandOver()
    ext.extendAppStart()
    assertTrue(ext.isActive)
    ext.clear()
    assertFalse(ext.isActive)
    assertNull(ext.extendedAppStartSpan)
  }

  @Test
  fun `getExtendedAppStartSpan returns null once the finish date is set even if still unfinished`() {
    // Same waitForChildren reentrancy as getExtendedEndTime: the finish timestamp is set before the
    // span's isFinished() flips, so the span must not be handed out for new children anymore.
    val ext = extension(windowOpen = true)
    val span = mock<ISpan>()
    whenever(span.isFinished).thenReturn(false)
    whenever(span.finishDate).thenReturn(SentryNanotimeDate())
    ext.registerHandOver(span = span)
    ext.extendAppStart()
    assertNull(ext.extendedAppStartSpan)
  }
}
