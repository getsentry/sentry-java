package io.sentry.metrics

import com.google.common.truth.Truth.assertThat
import io.sentry.DataCategory
import io.sentry.ISentryClient
import io.sentry.ISentryExecutorService
import io.sentry.SentryMetricsEvent
import io.sentry.SentryMetricsEvents
import io.sentry.SentryNanotimeDate
import io.sentry.SentryOptions
import io.sentry.clientreport.ClientReportTestHelper
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.DiscardedEvent
import io.sentry.protocol.SentryId
import io.sentry.test.DeferredExecutorService
import io.sentry.test.getProperty
import io.sentry.test.injectForField
import io.sentry.transport.ReusableCountLatch
import io.sentry.util.JsonSerializationUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class MetricsBatchProcessorTest {
  @Test
  fun `constructor does not submit processor work`() {
    val mockExecutor = mock<ISentryExecutorService>()

    MetricsBatchProcessor(SentryOptions(), mock(), mockExecutor)

    verifyNoInteractions(mockExecutor)
  }

  @Test
  fun `empty flush does not submit processor work`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = MetricsBatchProcessor(SentryOptions(), mock(), mockExecutor)

    processor.flush(0)

    verifyNoInteractions(mockExecutor)
  }

  @Test
  fun `close before first accepted item does not submit processor work`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = MetricsBatchProcessor(SentryOptions(), mock(), mockExecutor)

    processor.close(false)

    verify(mockExecutor).close(any())
    verify(mockExecutor, never()).schedule(any(), any())
    verify(mockExecutor, never()).submit(any<Runnable>())
  }

  @Test
  fun `restart close before first accepted item does not submit processor work`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = MetricsBatchProcessor(SentryOptions(), mock(), mockExecutor)

    processor.close(true)

    verify(mockExecutor).close(any())
    verify(mockExecutor, never()).schedule(any(), any())
    verify(mockExecutor, never()).submit(any<Runnable>())
  }

  @Test
  fun `item rejected during shutdown does not mark processor as used`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = MetricsBatchProcessor(SentryOptions(), mock(), mockExecutor)
    processor.close(false)

    processor.add(metricsEvent("rejected"))
    processor.flush(0)

    verify(mockExecutor, never()).schedule(any(), any())
    verify(mockExecutor, never()).submit(any<Runnable>())
  }

  @Test
  fun `item rejected due to queue capacity does not mark processor as used`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = MetricsBatchProcessor(SentryOptions(), mock(), mockExecutor)
    val pendingCount = processor.getProperty<ReusableCountLatch>("pendingCount")
    repeat(MetricsBatchProcessor.MAX_QUEUE_SIZE) { pendingCount.increment() }

    processor.add(metricsEvent("rejected"))
    processor.flush(0)

    verifyNoInteractions(mockExecutor)
  }

  @Test
  fun `flush and restart close submit processor work after first accepted item`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = MetricsBatchProcessor(SentryOptions(), mock(), mockExecutor)
    processor.add(metricsEvent("accepted"))

    processor.flush(0)
    processor.close(true)

    verify(mockExecutor, times(3)).schedule(any(), any())
    verify(mockExecutor).submit(any<Runnable>())
  }

  @Test
  fun `schedules another flush after previous flush has run`() {
    val mockClient = mock<ISentryClient>()
    val mockExecutor = DeferredExecutorService()
    val processor = MetricsBatchProcessor(SentryOptions(), mockClient)
    processor.injectForField("executorService", mockExecutor)

    processor.add(SentryMetricsEvent(SentryId(), SentryNanotimeDate(), "first", "gauge", 1.0))
    mockExecutor.runAll()

    processor.add(SentryMetricsEvent(SentryId(), SentryNanotimeDate(), "second", "gauge", 2.0))
    assertThat(mockExecutor.hasScheduledRunnables()).isTrue()
    mockExecutor.runAll()

    val captor = argumentCaptor<SentryMetricsEvents>()
    verify(mockClient, times(2)).captureBatchedMetricsEvents(captor.capture())
    assertThat(captor.allValues.flatMap { it.items }.map { it.name })
      .containsExactly("first", "second")
      .inOrder()
  }

  private fun metricsEvent(name: String) =
    SentryMetricsEvent(SentryId(), SentryNanotimeDate(), name, "gauge", 1.0)

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
    val droppedBytes =
      JsonSerializationUtils.byteSizeOf(options.serializer, options.logger, droppedMetricsEvent)
    val expectedEvents =
      mutableListOf(
        DiscardedEvent(DiscardReason.QUEUE_OVERFLOW.reason, DataCategory.TraceMetric.category, 1),
        DiscardedEvent(
          DiscardReason.QUEUE_OVERFLOW.reason,
          DataCategory.TraceMetricByte.category,
          droppedBytes,
        ),
      )

    ClientReportTestHelper.assertClientReport(options.clientReportRecorder, expectedEvents)
  }
}
