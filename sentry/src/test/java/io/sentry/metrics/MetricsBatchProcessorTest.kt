package io.sentry.metrics

import io.sentry.DataCategory
import io.sentry.ISentryClient
import io.sentry.SentryMetricsEvent
import io.sentry.SentryMetricsEvents
import io.sentry.SentryNanotimeDate
import io.sentry.SentryOptions
import io.sentry.clientreport.ClientReportTestHelper
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.DiscardedEvent
import io.sentry.protocol.SentryId
import io.sentry.test.DeferredExecutorService
import io.sentry.test.injectForField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MetricsBatchProcessorTest {
  @Test
  fun `drops metrics events after reaching MAX_QUEUE_SIZE limit`() {
    // given
    val mockClient = mock<ISentryClient>()
    val mockExecutor = DeferredExecutorService()
    val options = SentryOptions()
    val processor = MetricsBatchProcessor(options, mockClient)
    processor.injectForField("executorService", mockExecutor)

    for (i in 1..10001) {
      val logEvent =
        SentryMetricsEvent(SentryId(), SentryNanotimeDate(), "name $i", "gauge", i.toDouble())
      processor.add(logEvent)
    }

    // run twice since a non full batch would be scheduled at the end
    mockExecutor.runAll()
    mockExecutor.runAll()

    // assert that the transport received 10000 metrics events
    val captor = argumentCaptor<SentryMetricsEvents>()
    verify(mockClient, atLeast(1)).captureBatchedMetricsEvents(captor.capture())

    val allCapturedEvents = mutableListOf<SentryMetricsEvent>()
    captor.allValues.forEach { metricsEvents -> allCapturedEvents.addAll(metricsEvents.items) }

    assertEquals(10000, allCapturedEvents.size)

    // assert that metric 10001 did not make it but metric 10000 did get sent
    val metric10000Found = allCapturedEvents.any { it.name == "name 10000" }
    val metric10001Found = allCapturedEvents.any { it.name == "name 10001" }

    assertTrue(metric10000Found, "Metric 10000 should have been sent")
    assertFalse(metric10001Found, "Metric 10001 should not have been sent")
  }

  @Test
  fun `records client report when log event is dropped due to queue overflow`() {
    // given
    val mockClient = mock<ISentryClient>()
    val mockExecutor = DeferredExecutorService()
    val options = SentryOptions()
    val processor = MetricsBatchProcessor(options, mockClient)
    processor.injectForField("executorService", mockExecutor)

    // fill the queue to MAX_QUEUE_SIZE
    for (i in 1..10000) {
      val logEvent =
        SentryMetricsEvent(SentryId(), SentryNanotimeDate(), "name $i", "gauge", i.toDouble())
      processor.add(logEvent)
    }

    // add one more metrics event that should be dropped
    val droppedMetricsEvent =
      SentryMetricsEvent(SentryId(), SentryNanotimeDate(), "dropped metric", "gauge", 10001.0)
    processor.add(droppedMetricsEvent)

    // verify that a client report was recorded for the dropped metrics item
    val expectedEvents =
      mutableListOf(
        DiscardedEvent(DiscardReason.QUEUE_OVERFLOW.reason, DataCategory.TraceMetric.category, 1)
      )

    ClientReportTestHelper.assertClientReport(options.clientReportRecorder, expectedEvents)
  }
}
