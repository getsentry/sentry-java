package io.sentry.samples.spring.boot.jakarta.systemtest

import io.sentry.samples.spring.boot.jakarta.systemtest.util.TestHelper
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GraphqlProjectSystemTest {

    lateinit var testHelper: TestHelper

    @Before
    fun setup() {
        testHelper = TestHelper("http://localhost:8080")
    }

    @Test
    fun `project query works`() {
        testHelper.snapshotEnvelopeCount()

        val response = testHelper.graphqlClient.project("proj-slug")

        testHelper.ensureNoErrors(response)
        assertEquals("proj-slug", response?.data?.project?.slug)
        testHelper.ensureEnvelopCountIncreased()
    }

    @Test
    fun `project mutation works`() {
        testHelper.snapshotEnvelopeCount()

        val response = testHelper.graphqlClient.addProject("proj-slug")

        testHelper.ensureNoErrors(response)
        assertNotNull(response?.data?.addProject)
        testHelper.ensureEnvelopCountIncreased()
    }

    @Test
    fun `project mutation error`() {
        testHelper.snapshotEnvelopeCount()

        val response = testHelper.graphqlClient.addProject("addprojectcrash")

        testHelper.ensureErrorCount(response, 1)
        assertNull(response?.data?.addProject)
        testHelper.ensureEnvelopCountIncreased()
    }
}
