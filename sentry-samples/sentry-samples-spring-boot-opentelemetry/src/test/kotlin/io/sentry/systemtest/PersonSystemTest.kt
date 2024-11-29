package io.sentry.systemtest

import io.sentry.samples.spring.boot.Person
import io.sentry.systemtest.util.TestHelper
import org.junit.Before
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonSystemTest {

    lateinit var testHelper: TestHelper

    @Before
    fun setup() {
        testHelper = TestHelper("http://localhost:8080")
        testHelper.reset()
    }

    @Test
    fun `get person fails`() {
        val restClient = testHelper.restClient
        restClient.getPerson(1L)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, restClient.lastKnownStatusCode)

        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughOtelApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughSentryApi")
        }
    }

    @Test
    fun `create person works`() {
        val restClient = testHelper.restClient
        val person = Person("firstA", "lastB")
        val returnedPerson = restClient.createPerson(person)
        assertEquals(HttpStatus.OK, restClient.lastKnownStatusCode)

        assertEquals(person.firstName, returnedPerson!!.firstName)
        assertEquals(person.lastName, returnedPerson!!.lastName)

        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughOtelApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughSentryApi")
        }
    }

    @Test
    fun `create person creates transaction if no sampled flag in sentry-trace header`() {
        val restClient = testHelper.restClient
        val person = Person("firstA", "lastB")
        val returnedPerson = restClient.createPerson(
            person,
            mapOf(
                "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee",
                "baggage" to "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=f9118105af4a2d42b4124532cd1065ff,sentry-transaction=HTTP%20GET"
            )
        )
        assertEquals(HttpStatus.OK, restClient.lastKnownStatusCode)

        assertEquals(person.firstName, returnedPerson!!.firstName)
        assertEquals(person.lastName, returnedPerson!!.lastName)

        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughOtelApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughSentryApi")
        }
    }

    @Test
    fun `create person creates transaction if sampled true in sentry-trace header`() {
        val restClient = testHelper.restClient
        val person = Person("firstA", "lastB")
        val returnedPerson = restClient.createPerson(
            person,
            mapOf(
                "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
                "baggage" to "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=f9118105af4a2d42b4124532cd1065ff,sentry-transaction=HTTP%20GET"
            )
        )
        assertEquals(HttpStatus.OK, restClient.lastKnownStatusCode)

        assertEquals(person.firstName, returnedPerson!!.firstName)
        assertEquals(person.lastName, returnedPerson!!.lastName)

        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughOtelApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughSentryApi")
        }
    }

    @Test
    fun `create person does not create transaction if sampled false in sentry-trace header`() {
        val restClient = testHelper.restClient
        val person = Person("firstA", "lastB")
        val returnedPerson = restClient.createPerson(
            person,
            mapOf(
                "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-0",
                "baggage" to "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=f9118105af4a2d42b4124532cd1065ff,sentry-transaction=HTTP%20GET"
            )
        )
        assertEquals(HttpStatus.OK, restClient.lastKnownStatusCode)

        assertEquals(person.firstName, returnedPerson!!.firstName)
        assertEquals(person.lastName, returnedPerson!!.lastName)

        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughOtelApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughSentryApi")
        }
    }
}
