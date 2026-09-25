package io.sentry

import com.google.common.truth.Truth.assertThat
import io.sentry.clientreport.ClientReportTestHelper.Companion.assertClientReport
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.DiscardedEvent
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import kotlin.test.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
class SentryClientTransactionProfileTest(
  private val callback: String,
  private val hasProfile: Boolean,
) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "callback={0}, hasProfile={1}")
    fun data(): List<Array<Any>> =
      listOf("scope processor", "options processor", "beforeSendTransaction").flatMap { callback ->
        listOf(false, true).map { hasProfile -> arrayOf<Any>(callback, hasProfile) }
      }
  }

  @Test
  fun `transaction callback failure reports attached profile exactly once`() {
    val fixture = SentryClientTest.Fixture()
    val options = fixture.sentryOptions
    options.eventProcessors.clear()
    val scope = Scope(options)
    val onDiscard = mock<SentryOptions.OnDiscardCallback>()
    options.onDiscard = onDiscard
    val failure = IllegalStateException("callback failed")
    if (callback == "beforeSendTransaction") {
      options.setBeforeSendTransaction { _, _ -> throw failure }
    } else {
      val processor = mock<EventProcessor>()
      whenever(processor.process(any<SentryTransaction>(), any())).thenThrow(failure)
      if (callback == "scope processor") {
        scope.addEventProcessor(processor)
      } else {
        options.addEventProcessor(processor)
      }
    }

    val id =
      fixture
        .getSut()
        .captureTransaction(
          SentryTransaction(fixture.sentryTracer),
          fixture.sentryTracer.traceContext(),
          scope,
          null,
          if (hasProfile) fixture.profilingTraceData else null,
        )

    assertThat(id).isEqualTo(SentryId.EMPTY_ID)
    verify(fixture.transport, never()).send(any(), anyOrNull())
    val expected =
      mutableListOf(
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Transaction.category, 1),
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Span.category, 2),
      )
    if (hasProfile) {
      expected.add(
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Profile.category, 1)
      )
      verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Profile, 1)
    }
    assertClientReport(options.clientReportRecorder, expected)
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction, 1)
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 2)
    verifyNoMoreInteractions(onDiscard)
  }
}
