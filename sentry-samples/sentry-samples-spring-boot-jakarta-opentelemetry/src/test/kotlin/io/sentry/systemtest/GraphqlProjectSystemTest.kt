package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Before

class GraphqlProjectSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `project query works`() {
    val response = testHelper.graphqlClient.project("proj-slug")

    testHelper.ensureNoErrors(response)
    assertEquals("proj-slug", response?.data?.project?.slug)
    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "query ProjectQuery")
    }
  }

  @Test
  fun `project mutation works`() {
    val response = testHelper.graphqlClient.addProject("proj-slug")

    testHelper.ensureNoErrors(response)
    assertNotNull(response?.data?.addProject)
    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "mutation AddProjectMutation")
    }
  }

  @Test
  fun `project mutation error`() {
    val response = testHelper.graphqlClient.addProject("addprojectcrash")

    testHelper.ensureErrorCount(response, 1)
    assertNull(response?.data?.addProject)
    testHelper.ensureErrorReceived { error ->
      error.message?.message?.startsWith("Unresolved RuntimeException for executionId ") ?: false
    }
    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "mutation AddProjectMutation")
    }
  }
}
