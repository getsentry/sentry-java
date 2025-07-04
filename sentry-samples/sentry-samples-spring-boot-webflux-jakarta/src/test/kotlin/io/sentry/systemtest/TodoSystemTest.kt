package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Before

class TodoSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `get todo webclient works`() {
    val restClient = testHelper.restClient
    restClient.getTodoWebclient(1L)
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionContainSpanWithOpAndDescription(transaction, "http.client", "GET https://jsonplaceholder.typicode.com/todos/1")
    }
  }
}
