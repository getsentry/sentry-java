package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import org.junit.Before
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class TodoSystemTest {

    lateinit var testHelper: TestHelper

    @Before
    fun setup() {
        testHelper = TestHelper("http://localhost:8080")
        testHelper.reset()
    }

    @Test
    fun `get todo works`() {
        val restClient = testHelper.restClient
        restClient.getTodo(1L)
        assertEquals(HttpStatus.OK, restClient.lastKnownStatusCode)

        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "todoSpanOtelApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "todoSpanSentryApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "http.client")
        }
    }

    @Test
    fun `get todo webclient works`() {
        val restClient = testHelper.restClient
        restClient.getTodoWebclient(1L)
        assertEquals(HttpStatus.OK, restClient.lastKnownStatusCode)

        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "http.client")
        }
    }

    @Test
    fun `get todo restclient works`() {
        val restClient = testHelper.restClient
        restClient.getTodoRestClient(1L)
        assertEquals(HttpStatus.OK, restClient.lastKnownStatusCode)

        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "todoRestClientSpanOtelApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "todoRestClientSpanSentryApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "http.client")
        }
    }
}
