package io.sentry.test
import com.nhaarman.mockitokotlin2.check
import io.sentry.GsonSerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction
import java.lang.AssertionError

/**
 * Verifies is [SentryEnvelope] contains first event matching a predicate.
 */
fun checkEvent(predicate: (SentryEvent) -> Unit): SentryEnvelope {
    val options = SentryOptions().apply {
        setSerializer(GsonSerializer(SentryOptions()))
    }
    return check {
        val event: SentryEvent? = it.items.first().getEvent(options.serializer)
        if (event != null) {
            predicate(event)
        } else {
            throw AssertionError("event is null")
        }
    }
}

fun checkTransaction(predicate: (SentryTransaction) -> Unit): SentryEnvelope {
    val options = SentryOptions().apply {
        setSerializer(GsonSerializer(SentryOptions()))
    }
    return check {
        val transaction = it.items.first().getTransaction(options.serializer)
        if (transaction != null) {
            predicate(transaction)
        } else {
            throw AssertionError("transaction is null")
        }
    }
}
