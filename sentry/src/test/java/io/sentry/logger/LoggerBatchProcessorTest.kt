package io.sentry.logger

import io.sentry.ISentryClient
import io.sentry.SentryLogEvent
import io.sentry.SentryLogEvents
import io.sentry.SentryLogLevel
import io.sentry.SentryNanotimeDate
import io.sentry.SentryOptions
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

class LoggerBatchProcessorTest {
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
}
