package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import org.junit.Before
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
        assertEquals(500, restClient.lastKnownStatusCode)

        testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughOtelApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughSentryApi")
        }
    }

    @Test
    fun `create person works`() {
        val restClient = testHelper.restClient
        val person = Person("firstA", "lastB")
        val returnedPerson = restClient.createPerson(person)
        assertEquals(200, restClient.lastKnownStatusCode)

        assertEquals(person.firstName, returnedPerson!!.firstName)
        assertEquals(person.lastName, returnedPerson!!.lastName)

        testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughOtelApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughSentryApi")
        }
    }
}
