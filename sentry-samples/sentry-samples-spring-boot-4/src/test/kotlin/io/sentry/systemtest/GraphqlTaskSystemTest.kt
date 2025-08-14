package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Before

class GraphqlTaskSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `tasks and assignees query works`() {
    val response = testHelper.graphqlClient.tasksAndAssignees("project-slug")

    testHelper.ensureNoErrors(response)

    assertEquals(5, response?.data?.tasks?.size)

    val firstTask = response?.data?.tasks?.firstOrNull() ?: throw RuntimeException("no task")
    assertEquals("T1", firstTask.id)
    assertEquals("A3", firstTask.assigneeId)
    assertEquals("A3", firstTask.assignee?.id)
    assertEquals("C3", firstTask.creatorId)
    assertEquals("C3", firstTask.creator?.id)

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionContainSpanWithOpAndDescription(
        transaction,
        "graphql",
        "Query.tasks",
      ) &&
        testHelper.doesTransactionContainSpanWithOpAndDescription(
          transaction,
          "graphql",
          "Task.assignee",
        ) &&
        testHelper.doesTransactionContainSpanWithOpAndDescription(
          transaction,
          "graphql",
          "Task.creator",
        )
    }
  }
}
