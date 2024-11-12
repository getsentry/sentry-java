package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import org.junit.Before
import kotlin.test.Test

class GraphqlGreetingSystemTest {

    lateinit var testHelper: TestHelper

    @Before
    fun setup() {
        testHelper = TestHelper("http://localhost:8080")
        testHelper.reset()
    }

    @Test
    fun `greeting works`() {
        val response = testHelper.graphqlClient.greet("world")

        testHelper.ensureNoErrors(response)
        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithDescription(transaction, "Query.greeting")
        }
    }

    @Test
    fun `greeting error`() {
        val response = testHelper.graphqlClient.greet("crash")

        testHelper.ensureErrorCount(response, 1)
        testHelper.ensureErrorReceived { error ->
            error.message?.message?.startsWith("Unresolved RuntimeException for executionId ") ?: false
        }
        testHelper.ensureTransactionReceived { transaction ->
            testHelper.doesTransactionContainSpanWithDescription(transaction, "Query.greeting")
        }
    }
}
