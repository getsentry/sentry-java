package io.sentry.systemtest

import io.sentry.mockServerRequestTimeoutMillis
import io.sentry.systemtest.util.TestHelper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.http.HttpStatus
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TodoSystemTest {

    lateinit var testHelper: TestHelper
    var mockServer = MockWebServer()

    @BeforeTest
    fun setup() {
        testHelper = TestHelper("http://localhost:8080")
        println("resetting in before test")
        testHelper.reset()
        mockServer.start(48080)
        mockServer.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "userId": 1,
                      "id": 1,
                      "title": "delectus aut autem",
                      "completed": false
                    }
                    """.trimIndent()
                )
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setResponseCode(200)
        )
    }

    @AfterTest
    fun teardown() {
        mockServer.shutdown()
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
    fun `get todo works2`() {
        val restClient = testHelper.restClient
        println("resetting in test")
        testHelper.reset()
        restClient.getTodo(1L)
        assertEquals(HttpStatus.OK, restClient.lastKnownStatusCode)

        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithOp(transaction, "todoSpanOtelApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "todoSpanSentryApi") &&
                testHelper.doesTransactionContainSpanWithOp(transaction, "http.client")
        }

        val externalSystemRequest =
            mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!

        assertEquals("asdf", externalSystemRequest.headers["sentry-trace"])
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
