package io.sentry.test

import com.nhaarman.mockitokotlin2.check
import io.sentry.core.GsonSerializer
import io.sentry.core.NoOpLogger
import io.sentry.core.SentryEnvelope
import io.sentry.core.SentryEvent
import io.sentry.core.SentryOptions

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
