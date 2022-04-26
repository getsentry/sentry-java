package io.sentry.android.uitests.end2end.mockservers

import io.sentry.Sentry
import io.sentry.SentryEnvelope
import okhttp3.mockwebserver.MockResponse

class EnvelopeAsserter(val envelope: SentryEnvelope, val response: MockResponse) {
    private val unassertedItems = envelope.items.toMutableList()

    fun <T> assertItem(clazz: Class<T>): T {
        val item = unassertedItems.mapIndexed { index, it ->
            val deserialized = Sentry.getCurrentHub().options.serializer.deserialize(it.data.inputStream().reader(), clazz)
            deserialized?.let { Pair(index, it) }
        }.filterNotNull().firstOrNull()
            ?: throw AssertionError("No item found of type: ${clazz.name}")
        unassertedItems.removeAt(item.first)
        return item.second
    }

    fun assertNoOtherItems() {
        if (unassertedItems.isNotEmpty()) {
            throw AssertionError("There were other items: $unassertedItems")
        }
    }
}
