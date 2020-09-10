package io.sentry.test

import com.nhaarman.mockitokotlin2.check
import io.sentry.GsonSerializer
import io.sentry.NoOpLogger
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions

/**
 * Verifies is [SentryEnvelope] contains first event matching a predicate.
 */
inline fun checkEvent(noinline predicate: (SentryEvent) -> Unit): SentryEnvelope {
    val options = SentryOptions().apply {
        setSerializer(GsonSerializer(NoOpLogger.getInstance(), envelopeReader))
    }
    return check {
        val event = it.items.first().getEvent(options.serializer)!!
        predicate(event)
    }
}
