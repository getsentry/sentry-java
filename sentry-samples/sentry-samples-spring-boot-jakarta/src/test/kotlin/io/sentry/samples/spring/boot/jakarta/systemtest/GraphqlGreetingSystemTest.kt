package io.sentry.samples.spring.boot.jakarta.systemtest

import io.sentry.samples.spring.boot.jakarta.systemtest.util.TestHelper
import org.junit.Before
import kotlin.test.Test

class GraphqlGreetingSystemTest {

    lateinit var testHelper: TestHelper

    @Before
    fun setup() {
        testHelper = TestHelper("http://localhost:8080")
    }

    @Test
    fun `greeting works`() {
        testHelper.snapshotEnvelopeCount()

        val response = testHelper.graphqlClient.greet("world")

        testHelper.ensureNoErrors(response)
        testHelper.ensureEnvelopCountIncreased()
    }

    @Test
    fun `greeting error`() {
        testHelper.snapshotEnvelopeCount()

        val response = testHelper.graphqlClient.greet("crash")

        testHelper.ensureErrorCount(response, 1)
        testHelper.ensureEnvelopCountIncreased()
    }
}
