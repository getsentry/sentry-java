package io.sentry.systemtest

import io.sentry.samples.spring.boot.jakarta.Person
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
            testHelper.doesTransactionHaveOp(transaction, "http.server")
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
            testHelper.doesTransactionContainSpanWithOp(transaction, "PersonService.create") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "db.query")
        }
    }
}
