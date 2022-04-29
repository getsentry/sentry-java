package io.sentry.uitest.android.mockservers

import io.sentry.Sentry
import io.sentry.SentryEnvelope
import okhttp3.mockwebserver.MockResponse

/**
 * Class to make assertions on an envelope caught by [MockRelay].
 * It contains the sent envelope and the returned response, too.
 */
class EnvelopeAsserter(val envelope: SentryEnvelope, val response: MockResponse) {
    /** List of items to assert. */
    private val unassertedItems = envelope.items.toMutableList()

    /**
     * Asserts an envelope item that can be deserialized as an instance of [clazz] exists and returns the first one.
     * The asserted item is then removed from internal list of unasserted items.
     */
    fun <T> assertItem(clazz: Class<T>): T {
        val item = unassertedItems.mapIndexed { index, it ->
            val deserialized = Sentry.getCurrentHub().options.serializer.deserialize(it.data.inputStream().reader(), clazz)
            deserialized?.let { Pair(index, it) }
        }.filterNotNull().firstOrNull()
            ?: throw AssertionError("No item found of type: ${clazz.name}")
        unassertedItems.removeAt(item.first)
        return item.second
    }

    /** Asserts there are no other items in the envelope. */
    fun assertNoOtherItems() {
        if (unassertedItems.isNotEmpty()) {
            throw AssertionError("There were other items: $unassertedItems")
        }
    }
}
