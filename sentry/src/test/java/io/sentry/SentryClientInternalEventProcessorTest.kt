package io.sentry

import com.google.common.truth.Truth.assertThat
import io.sentry.clientreport.ClientReportTestHelper.Companion.assertClientReport
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.DiscardedEvent
import io.sentry.internal.eventprocessor.SentryEventProcessor
import io.sentry.protocol.Feedback
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import kotlin.test.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
class SentryClientInternalEventProcessorTest(private val onScope: Boolean) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "onScope={0}")
    fun data(): List<Array<Boolean>> = listOf(arrayOf(false), arrayOf(true))
  }

  private val fixture = SentryClientTest.Fixture()
  private val options = fixture.sentryOptions
  private val scope = Scope(options)
  private val processor = mock<SentryEventProcessor>()
  private val nextProcessor = mock<EventProcessor>()
  private val onDiscard = mock<SentryOptions.OnDiscardCallback>()
  private val logger = mock<ILogger>()
  private val failure = IllegalStateException("SDK processor failed")

  init {
    options.eventProcessors.clear()
    options.onDiscard = onDiscard
    options.setLogger(logger)
    options.logs.isEnabled = true
    options.metrics.isEnabled = true
    if (onScope) {
      scope.addEventProcessor(processor)
      scope.addEventProcessor(nextProcessor)
    } else {
      options.addEventProcessor(processor)
      options.addEventProcessor(nextProcessor)
    }
  }

  @Test
  fun `SDK event processor failure keeps event and runs remaining callbacks`() {
    val event = SentryEvent()
    val beforeSend = mock<SentryOptions.BeforeSendCallback>()
    whenever(processor.process(any<SentryEvent>(), any())).thenThrow(failure)
    whenever(nextProcessor.process(any<SentryEvent>(), any())).thenAnswer { it.arguments[0] }
    whenever(beforeSend.execute(any(), any())).thenAnswer { it.arguments[0] }
    options.beforeSend = beforeSend

    val id = fixture.getSut().captureEvent(event, scope)

    assertThat(id).isEqualTo(event.eventId)
    verify(nextProcessor).process(same(event), any())
    verify(beforeSend).execute(same(event), any())
    verify(fixture.transport)
      .send(check { assertThat(it.header.eventId).isEqualTo(id) }, anyOrNull())
    assertFailureLoggedWithoutLoss("event")
  }

  @Test
  fun `SDK transaction processor failure keeps transaction and spans`() {
    val transaction = SentryTransaction(fixture.sentryTracer)
    val beforeSend = mock<SentryOptions.BeforeSendTransactionCallback>()
    whenever(processor.process(any<SentryTransaction>(), any())).thenThrow(failure)
    whenever(nextProcessor.process(any<SentryTransaction>(), any())).thenAnswer { it.arguments[0] }
    whenever(beforeSend.execute(any(), any())).thenAnswer { it.arguments[0] }
    options.beforeSendTransaction = beforeSend

    val id = fixture.getSut().captureTransaction(transaction, scope, null)

    assertThat(id).isEqualTo(transaction.eventId)
    verify(nextProcessor).process(same(transaction), any())
    verify(beforeSend).execute(same(transaction), any())
    verify(fixture.transport)
      .send(
        check {
          val sent = it.items.first().getTransaction(options.serializer)!!
          assertThat(sent.eventId).isEqualTo(id)
          assertThat(sent.spans).hasSize(1)
        },
        anyOrNull(),
      )
    assertFailureLoggedWithoutLoss("transaction")
  }

  @Test
  fun `SDK feedback processor failure keeps feedback and runs remaining callbacks`() {
    val feedback = Feedback("message")
    val beforeSend = mock<SentryOptions.BeforeSendCallback>()
    whenever(processor.process(any<SentryEvent>(), any())).thenThrow(failure)
    whenever(nextProcessor.process(any<SentryEvent>(), any())).thenAnswer { it.arguments[0] }
    whenever(beforeSend.execute(any(), any())).thenAnswer { it.arguments[0] }
    options.beforeSendFeedback = beforeSend

    val id = fixture.getSut().captureFeedback(feedback, null, scope)

    assertThat(id).isNotEqualTo(SentryId.EMPTY_ID)
    verify(nextProcessor)
      .process(
        check<SentryEvent> {
          assertThat(it.contexts.feedback).isSameInstanceAs(feedback)
        },
        any(),
      )
    verify(beforeSend).execute(check { assertThat(it.eventId).isEqualTo(id) }, any())
    verify(fixture.transport)
      .send(check { assertThat(it.header.eventId).isEqualTo(id) }, anyOrNull())
    assertFailureLoggedWithoutLoss("feedback event")
  }

  @Test
  fun `SDK log processor failure keeps log and runs remaining callbacks`() {
    val event = SentryLogEvent(SentryId(), SentryNanotimeDate(), "message", SentryLogLevel.WARN)
    val beforeSend = mock<SentryOptions.Logs.BeforeSendLogCallback>()
    whenever(processor.process(any<SentryLogEvent>())).thenThrow(failure)
    whenever(nextProcessor.process(any<SentryLogEvent>())).thenAnswer { it.arguments[0] }
    whenever(beforeSend.execute(any())).thenAnswer { it.arguments[0] }
    options.logs.beforeSend = beforeSend

    fixture.getSut().captureLog(event, scope)

    verify(nextProcessor).process(same(event))
    verify(beforeSend).execute(same(event))
    verify(fixture.loggerBatchProcessor).add(same(event))
    assertFailureLoggedWithoutLoss("log event")
  }

  @Test
  fun `SDK metric processor failure keeps metric and runs remaining callbacks`() {
    val event = SentryMetricsEvent(SentryId(), SentryNanotimeDate(), "name", "gauge", 123.0)
    val beforeSend = mock<SentryOptions.Metrics.BeforeSendMetricCallback>()
    whenever(processor.process(any<SentryMetricsEvent>(), any())).thenThrow(failure)
    whenever(nextProcessor.process(any<SentryMetricsEvent>(), any())).thenAnswer { it.arguments[0] }
    whenever(beforeSend.execute(any(), any())).thenAnswer { it.arguments[0] }
    options.metrics.beforeSend = beforeSend

    fixture.getSut().captureMetric(event, scope, null)

    verify(nextProcessor).process(same(event), any())
    verify(beforeSend).execute(same(event), any())
    verify(fixture.metricsBatchProcessor).add(same(event))
    assertFailureLoggedWithoutLoss("metrics event")
  }

  @Test
  fun `SDK processor returning null still drops event as event_processor`() {
    whenever(processor.process(any<SentryEvent>(), any())).thenReturn(null)

    val id = fixture.getSut().captureEvent(SentryEvent(), scope)

    assertThat(id).isEqualTo(SentryId.EMPTY_ID)
    verify(nextProcessor, never()).process(any<SentryEvent>(), any())
    verify(fixture.transport, never()).send(any(), anyOrNull())
    assertClientReport(
      options.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1)),
    )
  }

  @Test
  fun `customer processor failure after SDK processor failure still drops event`() {
    whenever(processor.process(any<SentryEvent>(), any())).thenThrow(failure)
    whenever(nextProcessor.process(any<SentryEvent>(), any()))
      .thenThrow(IllegalArgumentException("customer"))
    val beforeSend = mock<SentryOptions.BeforeSendCallback>()
    options.beforeSend = beforeSend

    val id = fixture.getSut().captureEvent(SentryEvent(), scope)

    assertThat(id).isEqualTo(SentryId.EMPTY_ID)
    verify(nextProcessor).process(any<SentryEvent>(), any())
    verifyNoInteractions(beforeSend)
    verify(fixture.transport, never()).send(any(), anyOrNull())
    assertClientReport(
      options.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Error.category, 1)),
    )
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Error, 1)
  }

  @Test
  fun `spans removed before SDK processor failure retain event_processor accounting`() {
    val transaction = SentryTransaction(fixture.sentryTracer)
    whenever(processor.process(any<SentryTransaction>(), any())).doAnswer {
      transaction.spans.clear()
      throw failure
    }
    whenever(nextProcessor.process(any<SentryTransaction>(), any())).thenAnswer { it.arguments[0] }

    val id = fixture.getSut().captureTransaction(transaction, scope, null)

    assertThat(id).isEqualTo(transaction.eventId)
    verify(nextProcessor).process(same(transaction), any())
    verify(fixture.transport).send(any(), anyOrNull())
    assertClientReport(
      options.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 1)),
    )
  }

  private fun assertFailureLoggedWithoutLoss(item: String) {
    verify(logger)
      .log(
        eq(SentryLevel.ERROR),
        same(failure),
        eq("An exception occurred while processing $item by processor: %s"),
        eq(processor.javaClass.name),
      )
    assertClientReport(options.clientReportRecorder, emptyList())
    verifyNoInteractions(onDiscard)
  }
}
