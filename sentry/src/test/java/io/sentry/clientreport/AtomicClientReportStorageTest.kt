package io.sentry.clientreport

import io.sentry.DataCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class AtomicClientReportStorageTest {
  @Test
  fun canAddSingleCount() {
    val storage = AtomicClientReportStorage()

    storage.addCount(
      ClientReportKey(DiscardReason.NETWORK_ERROR.reason, DataCategory.Error.category),
      1,
    )

    val discardedEvents = storage.resetCountsAndGet()
    assertEquals(1, discardedEvents.size)
    assertEquals(
      1,
      discardedEvents
        .first {
          it.reason == DiscardReason.NETWORK_ERROR.reason &&
            it.category == DataCategory.Error.category
        }
        .quantity,
    )
  }

  @Test
  fun countIsReset() {
    val storage = AtomicClientReportStorage()

    storage.addCount(
      ClientReportKey(DiscardReason.NETWORK_ERROR.reason, DataCategory.Error.category),
      1,
    )

    val discardedEventsBeforeReset = storage.resetCountsAndGet()
    assertEquals(1, discardedEventsBeforeReset.size)
    assertEquals(
      1,
      discardedEventsBeforeReset
        .first {
          it.reason == DiscardReason.NETWORK_ERROR.reason &&
            it.category == DataCategory.Error.category
        }
        .quantity,
    )

    val discardedEventsAfterReset = storage.resetCountsAndGet()
    assertEquals(0, discardedEventsAfterReset.size)
  }

  @Test
  fun canAddMultipleCounts() {
    val storage = AtomicClientReportStorage()

    storage.addCount(
      ClientReportKey(DiscardReason.NETWORK_ERROR.reason, DataCategory.Error.category),
      1,
    )
    storage.addCount(
      ClientReportKey(DiscardReason.NETWORK_ERROR.reason, DataCategory.Error.category),
      1,
    )
    storage.addCount(
      ClientReportKey(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category),
      1,
    )
    storage.addCount(
      ClientReportKey(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Transaction.category),
      1,
    )
    storage.addCount(
      ClientReportKey(DiscardReason.BEFORE_SEND.reason, DataCategory.Error.category),
      1,
    )
    storage.addCount(
      ClientReportKey(DiscardReason.CACHE_OVERFLOW.reason, DataCategory.Error.category),
      1,
    )
    storage.addCount(
      ClientReportKey(DiscardReason.RATELIMIT_BACKOFF.reason, DataCategory.Error.category),
      1,
    )
    storage.addCount(
      ClientReportKey(DiscardReason.QUEUE_OVERFLOW.reason, DataCategory.Error.category),
      1,
    )

    val discardedEvents = storage.resetCountsAndGet()
    assertEquals(7, discardedEvents.size)
    assertEquals(
      2,
      discardedEvents
        .first {
          it.reason == DiscardReason.NETWORK_ERROR.reason &&
            it.category == DataCategory.Error.category
        }
        .quantity,
    )
    assertEquals(
      1,
      discardedEvents
        .first {
          it.reason == DiscardReason.EVENT_PROCESSOR.reason &&
            it.category == DataCategory.Error.category
        }
        .quantity,
    )
    assertEquals(
      1,
      discardedEvents
        .first {
          it.reason == DiscardReason.EVENT_PROCESSOR.reason &&
            it.category == DataCategory.Transaction.category
        }
        .quantity,
    )
    assertEquals(
      1,
      discardedEvents
        .first {
          it.reason == DiscardReason.BEFORE_SEND.reason &&
            it.category == DataCategory.Error.category
        }
        .quantity,
    )
    assertEquals(
      1,
      discardedEvents
        .first {
          it.reason == DiscardReason.CACHE_OVERFLOW.reason &&
            it.category == DataCategory.Error.category
        }
        .quantity,
    )
    assertEquals(
      1,
      discardedEvents
        .first {
          it.reason == DiscardReason.RATELIMIT_BACKOFF.reason &&
            it.category == DataCategory.Error.category
        }
        .quantity,
    )
    assertEquals(
      1,
      discardedEvents
        .first {
          it.reason == DiscardReason.QUEUE_OVERFLOW.reason &&
            it.category == DataCategory.Error.category
        }
        .quantity,
    )
  }
}
