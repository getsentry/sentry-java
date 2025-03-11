package io.sentry.systemtest

import io.sentry.protocol.SentryId
import io.sentry.systemtest.util.TestHelper
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DistributedTracingSystemTest {

    lateinit var testHelper: TestHelper

    @Before
    fun setup() {
        testHelper = TestHelper("http://localhost:8080")
        testHelper.reset()
    }

    @Test
    fun `get person distributed tracing`() {
        val traceId = SentryId()
        val restClient = testHelper.restClient
        restClient.getPersonDistributedTracing(
            1L,
            mapOf(
                "sentry-trace" to "$traceId-424cffc8f94feeee-1",
                "baggage" to "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET"
            )
        )
        assertEquals(500, restClient.lastKnownStatusCode)

        testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
            transaction.transaction == "GET /tracing/{id}" &&
                testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())
        }

        testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
            transaction.transaction == "GET /person/{id}" &&
                testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())
        }
    }

    @Test
    fun `get person distributed tracing with sampled false`() {
        val traceId = SentryId()
        val restClient = testHelper.restClient
        restClient.getPersonDistributedTracing(
            1L,
            mapOf(
                "sentry-trace" to "$traceId-424cffc8f94feeee-0",
                "baggage" to "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=false,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET"
            )
        )
        assertEquals(500, restClient.lastKnownStatusCode)

        testHelper.ensureNoTransactionReceived { transaction, envelopeHeader ->
            transaction.transaction == "GET /tracing/{id}"
        }

        testHelper.ensureNoTransactionReceived { transaction, envelopeHeader ->
            transaction.transaction == "GET /person/{id}"
        }
    }

    @Test
    fun `get person distributed tracing without sample_rand`() {
        val traceId = SentryId()
        val restClient = testHelper.restClient
        restClient.getPersonDistributedTracing(
            1L,
            mapOf(
                "sentry-trace" to "$traceId-424cffc8f94feeee-1",
                "baggage" to "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET"
            )
        )
        assertEquals(500, restClient.lastKnownStatusCode)

        var sampleRand1: String? = null
        var sampleRand2: String? = null

        testHelper.ensureTransactionReceived { transaction, envelopeHeader ->

            val matches = transaction.transaction == "GET /tracing/{id}" &&
                envelopeHeader.traceContext!!.traceId == traceId &&
                testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())

            if (matches) {
                testHelper.logObject(envelopeHeader)
                testHelper.logObject(transaction)
                sampleRand1 = envelopeHeader.traceContext?.sampleRand
            }

            matches
        }

        testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
            val matches = transaction.transaction == "GET /person/{id}" &&
                envelopeHeader.traceContext!!.traceId == traceId &&
                testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())

            if (matches) {
                testHelper.logObject(envelopeHeader)
                testHelper.logObject(transaction)
                sampleRand2 = envelopeHeader.traceContext?.sampleRand
            }

            matches
        }

        assertEquals(sampleRand1, sampleRand2)
    }

    @Test
    fun `get person distributed tracing updates sample_rate on deferred decision`() {
        val traceId = SentryId()
        val restClient = testHelper.restClient
        restClient.getPersonDistributedTracing(
            1L,
            mapOf(
                "sentry-trace" to "$traceId-424cffc8f94feeee",
                "baggage" to "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=0.5,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET"
            )
        )
        assertEquals(500, restClient.lastKnownStatusCode)

        var sampleRate1: String? = null
        var sampleRate2: String? = null

        testHelper.ensureTransactionReceived { transaction, envelopeHeader ->

            val matches = transaction.transaction == "GET /tracing/{id}" &&
                envelopeHeader.traceContext!!.traceId == traceId &&
                testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())

            if (matches) {
                testHelper.logObject(envelopeHeader)
                testHelper.logObject(transaction)
                sampleRate1 = envelopeHeader.traceContext?.sampleRate
            }

            matches
        }

        testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
            val matches = transaction.transaction == "GET /person/{id}" &&
                envelopeHeader.traceContext!!.traceId == traceId &&
                testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())

            if (matches) {
                testHelper.logObject(envelopeHeader)
                testHelper.logObject(transaction)
                sampleRate2 = envelopeHeader.traceContext?.sampleRate
            }

            matches
        }

        assertEquals(sampleRate1, sampleRate2)
        assertNotEquals(sampleRate1, "0.5")
    }

    @Test
    fun `create person distributed tracing`() {
        val traceId = SentryId()
        val restClient = testHelper.restClient
        val person = Person("firstA", "lastB")
        val returnedPerson = restClient.createPersonDistributedTracing(
            person,
            mapOf(
                "sentry-trace" to "$traceId-424cffc8f94feeee-1",
                "baggage" to "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET"
            )
        )
        assertEquals(200, restClient.lastKnownStatusCode)

        assertEquals(person.firstName, returnedPerson!!.firstName)
        assertEquals(person.lastName, returnedPerson!!.lastName)

        testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
            transaction.transaction == "POST /tracing/" &&
                testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())
        }

        testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
            transaction.transaction == "POST /person/" &&
                testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())
        }
    }
}
