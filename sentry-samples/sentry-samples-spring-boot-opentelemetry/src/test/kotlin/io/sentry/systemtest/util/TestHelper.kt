package io.sentry.systemtest.util

import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Operation
import io.sentry.JsonSerializer
import io.sentry.SentryEvent
import io.sentry.SentryItemType
import io.sentry.SentryOptions
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import io.sentry.systemtest.graphql.GraphqlTestClient
import java.io.PrintWriter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestHelper(backendUrl: String) {

    val restClient: RestTestClient
    val graphqlClient: GraphqlTestClient
    val sentryClient: SentryMockServerClient
    val jsonSerializer: JsonSerializer

    var envelopeCounts: EnvelopeCounts? = null

    init {
        restClient = RestTestClient(backendUrl)
        sentryClient = SentryMockServerClient("http://localhost:8000")
        graphqlClient = GraphqlTestClient(backendUrl)
        jsonSerializer = JsonSerializer(SentryOptions.empty())
    }

    fun snapshotEnvelopeCount() {
        envelopeCounts = sentryClient.getEnvelopeCount()
    }

    fun ensureEnvelopeCountIncreased() {
        Thread.sleep(1000)
        val envelopeCountsAfter = sentryClient.getEnvelopeCount()
        assertTrue(envelopeCountsAfter!!.envelopes!! > envelopeCounts!!.envelopes!!)
    }

    fun ensureEnvelopeReceived(callback: ((String) -> Boolean)) {
        Thread.sleep(10000)
        val envelopes = sentryClient.getEnvelopes()
        assertNotNull(envelopes.envelopes)
        envelopes.envelopes.forEach { envelopeString ->
            val didMatch = callback(envelopeString)
            if (didMatch) {
                return
            }
        }
        throw RuntimeException("Unable to find matching envelope received by relay")
    }

    fun ensureTransactionReceived(callback: ((SentryTransaction) -> Boolean)) {
        ensureEnvelopeReceived { envelopeString ->
            val deserializeEnvelope =
                jsonSerializer.deserializeEnvelope(envelopeString.byteInputStream())
            if (deserializeEnvelope == null) {
                return@ensureEnvelopeReceived false
            }

            val transactionItem =
                deserializeEnvelope.items.firstOrNull { it.header.type == SentryItemType.Transaction }
            if (transactionItem == null) {
                return@ensureEnvelopeReceived false
            }

            val transaction = transactionItem.getTransaction(jsonSerializer)
            if (transaction == null) {
                return@ensureEnvelopeReceived false
            }

            callback(transaction)
        }
    }

    fun ensureErrorReceived(callback: ((SentryEvent) -> Boolean)) {
        ensureEnvelopeReceived { envelopeString ->
            val deserializeEnvelope =
                jsonSerializer.deserializeEnvelope(envelopeString.byteInputStream())
            if (deserializeEnvelope == null) {
                return@ensureEnvelopeReceived false
            }

            val errorItem =
                deserializeEnvelope.items.firstOrNull { it.header.type == SentryItemType.Event }
            if (errorItem == null) {
                return@ensureEnvelopeReceived false
            }

            val error = errorItem.getEvent(jsonSerializer)
            if (error == null) {
                return@ensureEnvelopeReceived false
            }

            val callbackResult = callback(error)
            if (!callbackResult) {
                println("found an error event but it did not match:")
                logObject(error)
            }
            callbackResult
        }
    }

    fun ensureTransactionWithSpanReceived(callback: ((SentrySpan) -> Boolean)) {
        ensureTransactionReceived { transaction ->
            transaction.spans.forEach { span ->
                val callbackResult = callback(span)
                if (callbackResult) {
                    return@ensureTransactionReceived true
                }
            }
            false
        }
    }

    fun reset() {
        sentryClient.reset()
    }

    fun logObject(obj: Any?) {
        obj ?: return
        PrintWriter(System.out).use {
            jsonSerializer.serialize(obj, it)
        }
    }

    fun <T : Operation.Data> ensureNoErrors(response: ApolloResponse<T>?) {
        response ?: throw RuntimeException("no response")
        assertFalse(response.hasErrors())
    }

    fun <T : Operation.Data> ensureErrorCount(response: ApolloResponse<T>?, errorCount: Int) {
        response ?: throw RuntimeException("no response")
        assertEquals(errorCount, response.errors?.size)
    }

    fun doesTransactionContainSpanWithOp(transaction: SentryTransaction, op: String): Boolean {
        val span = transaction.spans.firstOrNull { span -> span.op == op }
        if (span == null) {
            println("Unable to find span with op $op in transaction:")
            logObject(transaction)
            return false
        }

        return true
    }

    fun doesTransactionContainSpanWithDescription(transaction: SentryTransaction, description: String): Boolean {
        val span = transaction.spans.firstOrNull { span -> span.description == description }
        if (span == null) {
            println("Unable to find span with description $description in transaction:")
            logObject(transaction)
            return false
        }

        return true
    }

    fun doesTransactionHaveOp(transaction: SentryTransaction, op: String): Boolean {
        val matches = transaction.contexts.trace?.operation == op
        if (!matches) {
            println("Unable to find transaction with op $op:")
            logObject(transaction)
            return false
        }

        return true
    }
}
