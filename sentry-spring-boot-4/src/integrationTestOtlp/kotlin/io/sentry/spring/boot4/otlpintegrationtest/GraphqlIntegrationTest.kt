package io.sentry.spring.boot4.otlpintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.SentryTransaction
import io.sentry.test.event
import io.sentry.test.hasSpanWithOpAndDescription
import io.sentry.test.transaction
import kotlin.test.Test
import tools.jackson.databind.JsonNode

class GraphqlIntegrationTest : IntegrationTest() {

  @Test
  fun `query is traced`() {
    val response = graphql("""query { greeting(name: "world") }""")

    assertThat(response.has("errors")).isFalse()
    assertThat(response["data"]["greeting"].asString()).isEqualTo("Hello world!")
    awaitTransactionWithSpan("Query.greeting")
  }

  @Test
  fun `resolver error is reported and the query traced`() {
    val response = graphql("""query { greeting(name: "crash") }""")

    assertThat(response["errors"].size()).isEqualTo(1)
    awaitError("causing an error for crash")
    awaitTransactionWithSpan("Query.greeting")
  }

  @Test
  fun `query with a schema mapping is traced`() {
    val response = graphql("""query { project(slug: "proj-slug") { slug status } }""")

    assertThat(response.has("errors")).isFalse()
    assertThat(response["data"]["project"]["slug"].asString()).isEqualTo("proj-slug")
    awaitTransactionWithSpan("Query.project")
  }

  @Test
  fun `mutation is traced`() {
    val response = graphql("""mutation { addProject(slug: "proj-slug") }""")

    assertThat(response.has("errors")).isFalse()
    assertThat(response["data"]["addProject"].asString()).isNotEmpty()
    awaitTransactionWithSpan("Mutation.addProject")
  }

  @Test
  fun `mutation error is reported and the mutation traced`() {
    val response = graphql("""mutation { addProject(slug: "addprojectcrash") }""")

    assertThat(response["errors"].size()).isEqualTo(1)
    assertThat(response.path("data").path("addProject").asString(null)).isNull()
    awaitError("causing a project add error for addprojectcrash")
    awaitTransactionWithSpan("Mutation.addProject")
  }

  @Test
  fun `batch loaded fields are traced`() {
    val response =
      graphql(
        """query { tasks(projectSlug: "project-slug") { id assigneeId assignee { id } creatorId creator { id } } }"""
      )

    assertThat(response.has("errors")).isFalse()
    val tasks = response["data"]["tasks"]
    assertThat(tasks.size()).isEqualTo(5)
    assertThat(tasks[0]["id"].asString()).isEqualTo("T1")
    assertThat(tasks[0]["assigneeId"].asString()).isEqualTo("A3")
    assertThat(tasks[0]["creatorId"].asString()).isEqualTo("C3")
    assertThat(tasks[0]["assignee"]["id"].asString()).isEqualTo("A3")
    assertThat(tasks[0]["creator"]["id"].asString()).isEqualTo("C3")

    val transaction = awaitTransactionWithSpan("Query.tasks")
    assertThat(transaction.hasSpanWithOpAndDescription("graphql", "Task.assignee")).isTrue()
    assertThat(transaction.hasSpanWithOpAndDescription("graphql", "Task.creator")).isTrue()
  }

  private fun graphql(query: String): JsonNode {
    val response =
      restTemplate.postForEntity("/graphql", mapOf("query" to query), JsonNode::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)
    return response.body!!
  }

  private fun awaitTransactionWithSpan(description: String): SentryTransaction =
    server
      .awaitEnvelope {
        it.transaction()?.let { t ->
          t.transaction == "POST /graphql" &&
            t.spans.any { span -> span.op == "graphql" && span.description == description }
        } == true
      }
      .transaction()!!

  /**
   * Waits for the error for [exceptionMessage]. It points at the OpenTelemetry trace rather than at
   * Sentry's transaction (see PersonIntegrationTest), so it can't be tied to the transaction.
   */
  private fun awaitError(exceptionMessage: String) {
    server.awaitEnvelope { envelope ->
      envelope.event()?.let { event ->
        event.message?.message?.startsWith("Unresolved RuntimeException for executionId ") ==
          true && event.exceptions?.any { it.value == exceptionMessage } == true
      } == true
    }
  }
}
