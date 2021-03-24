package io.sentry.test

import com.nhaarman.mockitokotlin2.check
import io.sentry.GsonSerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryItemType
import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction

/**
 * Verifies that [SentryEnvelope] contains an event matching a predicate.
 */
inline fun checkEvent(noinline predicate: (SentryEvent) -> Unit): SentryEnvelope {
    val options = SentryOptions().apply {
        setSerializer(GsonSerializer(SentryOptions()))
    }
    return check {
        val event = it.items.firstOrNull { item -> item.header.type == SentryItemType.Event }?.getEvent(options.serializer)
        if (event != null) {
            predicate(event)
        }
    }
}

/**
 * Verifies that [SentryEnvelope] contains a transaction matching a predicate.
 */
inline fun checkTransaction(noinline predicate: (SentryTransaction) -> Unit): SentryEnvelope {
    val options = SentryOptions().apply {
        setSerializer(GsonSerializer(this))
    }
    return check {
        val event = it.items.firstOrNull { item -> item.header.type == SentryItemType.Transaction }?.getTransaction(options.serializer)
        if (event != null) {
            predicate(event)
        }
    }
}
