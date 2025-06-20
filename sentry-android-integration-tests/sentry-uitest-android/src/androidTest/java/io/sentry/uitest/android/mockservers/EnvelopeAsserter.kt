package io.sentry.uitest.android.mockservers

import io.sentry.ProfilingTraceData
import io.sentry.SentryEnvelope
import io.sentry.android.core.AndroidLogger
import io.sentry.assertEnvelopeItem
import io.sentry.assertEnvelopeProfile
import io.sentry.assertEnvelopeTransaction
import io.sentry.protocol.SentryTransaction
import okhttp3.mockwebserver.MockResponse

/**
 * Class to make assertions on an envelope caught by [MockRelay]. It contains the sent envelope and
 * the returned response, too.
 */
class EnvelopeAsserter(val envelope: SentryEnvelope, val response: MockResponse) {
  /** List of items to assert. */
  val unassertedItems = envelope.items.toMutableList()

  /**
   * Asserts an envelope item of [T] exists and returns the first one. The asserted item is then
   * removed from internal list of unasserted items.
   */
  inline fun <reified T> assertItem(): T =
    assertEnvelopeItem(unassertedItems) { index, item ->
      unassertedItems.removeAt(index)
      return item
    }

  /**
   * Asserts a transaction exists and returns the first one. It is then removed from internal list
   * of unasserted items.
   */
  fun assertTransaction(): SentryTransaction =
    assertEnvelopeTransaction(unassertedItems, AndroidLogger()) { index, item ->
      unassertedItems.removeAt(index)
      return item
    }

  /**
   * Asserts a profile exists and returns the first one. It is then removed from internal list of
   * unasserted items.
   */
  inline fun assertProfile(): ProfilingTraceData =
    assertEnvelopeProfile(unassertedItems) { index, item ->
      unassertedItems.removeAt(index)
      return item
    }

  /** Asserts there are no other items in the envelope. */
  fun assertNoOtherItems() {
    if (unassertedItems.isNotEmpty()) {
      throw AssertionError("There were other items: $unassertedItems")
    }
  }
}
