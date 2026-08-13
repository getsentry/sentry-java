package io.sentry.logger

import com.google.common.truth.Truth.assertThat
import io.sentry.DataCategory
import io.sentry.ISentryClient
import io.sentry.ISentryExecutorService
import io.sentry.SentryLogEvent
import io.sentry.SentryLogEvents
import io.sentry.SentryLogLevel
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

class LoggerBatchProcessorTest {
  @Test
  fun `constructor does not submit processor work`() {
    val mockExecutor = mock<ISentryExecutorService>()

    LoggerBatchProcessor(SentryOptions(), mock(), mockExecutor)

    verifyNoInteractions(mockExecutor)
  }

  @Test
  fun `empty flush does not submit processor work`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = LoggerBatchProcessor(SentryOptions(), mock(), mockExecutor)

    processor.flush(0)

    verifyNoInteractions(mockExecutor)
  }

  @Test
  fun `close before first accepted item does not submit processor work`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = LoggerBatchProcessor(SentryOptions(), mock(), mockExecutor)

    processor.close(false)

    verify(mockExecutor).close(any())
    verify(mockExecutor, never()).schedule(any(), any())
    verify(mockExecutor, never()).submit(any<Runnable>())
  }

  @Test
  fun `restart close before first accepted item does not submit processor work`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = LoggerBatchProcessor(SentryOptions(), mock(), mockExecutor)

    processor.close(true)

    verify(mockExecutor).close(any())
    verify(mockExecutor, never()).schedule(any(), any())
    verify(mockExecutor, never()).submit(any<Runnable>())
  }

  @Test
  fun `item rejected during shutdown does not mark processor as used`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = LoggerBatchProcessor(SentryOptions(), mock(), mockExecutor)
    processor.close(false)

    processor.add(logEvent("rejected"))
    processor.flush(0)

    verify(mockExecutor, never()).schedule(any(), any())
    verify(mockExecutor, never()).submit(any<Runnable>())
  }

  @Test
  fun `item rejected due to queue capacity does not mark processor as used`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = LoggerBatchProcessor(SentryOptions(), mock(), mockExecutor)
    val pendingCount = processor.getProperty<ReusableCountLatch>("pendingCount")
    repeat(LoggerBatchProcessor.MAX_QUEUE_SIZE) { pendingCount.increment() }

    processor.add(logEvent("rejected"))
    processor.flush(0)

    verifyNoInteractions(mockExecutor)
  }

  @Test
  fun `flush and restart close submit processor work after first accepted item`() {
    val mockExecutor = mock<ISentryExecutorService>()
    val processor = LoggerBatchProcessor(SentryOptions(), mock(), mockExecutor)
    processor.add(logEvent("accepted"))

    processor.flush(0)
    processor.close(true)

    verify(mockExecutor, times(3)).schedule(any(), any())
    verify(mockExecutor).submit(any<Runnable>())
  }

  @Test
  fun `schedules another flush after previous flush has run`() {
    val mockClient = mock<ISentryClient>()
    val mockExecutor = DeferredExecutorService()
    val processor = LoggerBatchProcessor(SentryOptions(), mockClient, mockExecutor)

    processor.add(SentryLogEvent(SentryId(), SentryNanotimeDate(), "first", SentryLogLevel.INFO))
    mockExecutor.runAll()

    processor.add(SentryLogEvent(SentryId(), SentryNanotimeDate(), "second", SentryLogLevel.INFO))
    assertThat(mockExecutor.hasScheduledRunnables()).isTrue()
    mockExecutor.runAll()

    val captor = argumentCaptor<SentryLogEvents>()
    verify(mockClient, times(2)).captureBatchedLogEvents(captor.capture())
    assertThat(captor.allValues.flatMap { it.items }.map { it.body })
      .containsExactly("first", "second")
      .inOrder()
  }

  private fun logEvent(body: String) =
    SentryLogEvent(SentryId(), SentryNanotimeDate(), body, SentryLogLevel.INFO)

  @Test
  fun `drops log events after reaching MAX_QUEUE_SIZE limit`() {
    // given
    val mockClient = mock<ISentryClient>()
    val mockExecutor = DeferredExecutorService()
    val options = SentryOptions()
    val processor = LoggerBatchProcessor(options, mockClient)
    processor.injectForField("executorService", mockExecutor)

    for (i in 1..1001) {
      val logEvent =
        SentryLogEvent(SentryId(), SentryNanotimeDate(), "log message $i", SentryLogLevel.INFO)
      processor.add(logEvent)
    }

    // run twice since a non full batch would be scheduled at the end
    mockExecutor.runAll()
    mockExecutor.runAll()

    // assert that the transport received 1000 log events
    val captor = argumentCaptor<SentryLogEvents>()
    verify(mockClient, atLeast(1)).captureBatchedLogEvents(captor.capture())

    val allCapturedEvents = mutableListOf<SentryLogEvent>()
    captor.allValues.forEach { logEvents -> allCapturedEvents.addAll(logEvents.items) }

    assertEquals(1000, allCapturedEvents.size)

    // assert that log 1001 did not make it but log 1000 did get sent
    val log1000Found = allCapturedEvents.any { it.body == "log message 1000" }
    val log1001Found = allCapturedEvents.any { it.body == "log message 1001" }

    assertTrue(log1000Found, "Log 1000 should have been sent")
    assertFalse(log1001Found, "Log 1001 should not have been sent")
  }

  @Test
  fun `records client report when log event is dropped due to queue overflow`() {
    // given
    val mockClient = mock<ISentryClient>()
    val mockExecutor = DeferredExecutorService()
    val options = SentryOptions()
    val processor = LoggerBatchProcessor(options, mockClient)
    processor.injectForField("executorService", mockExecutor)

    // fill the queue to MAX_QUEUE_SIZE
    for (i in 1..1000) {
      val logEvent =
        SentryLogEvent(SentryId(), SentryNanotimeDate(), "log message $i", SentryLogLevel.INFO)
      processor.add(logEvent)
    }

    // add one more log event that should be dropped
    val droppedLogEvent =
      SentryLogEvent(SentryId(), SentryNanotimeDate(), "dropped log", SentryLogLevel.INFO)
    processor.add(droppedLogEvent)

    // calculate expected bytes for the dropped log event
    val expectedBytes =
      JsonSerializationUtils.byteSizeOf(options.serializer, options.logger, droppedLogEvent)

    // verify that a client report was recorded for the dropped log item and bytes
    val expectedEvents =
      mutableListOf(
        DiscardedEvent(DiscardReason.QUEUE_OVERFLOW.reason, DataCategory.LogItem.category, 1),
        DiscardedEvent(
          DiscardReason.QUEUE_OVERFLOW.reason,
          DataCategory.LogByte.category,
          expectedBytes,
        ),
      )

    ClientReportTestHelper.assertClientReport(options.clientReportRecorder, expectedEvents)
  }
}
