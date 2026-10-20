package io.sentry.spring.boot.integrationtest

import com.fasterxml.jackson.databind.JsonNode
import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.SentryTransaction
import io.sentry.test.event
import io.sentry.test.hasSpanWithOpAndDescription
import io.sentry.test.transaction
import kotlin.test.Test

class GraphqlIntegrationTest : IntegrationTest() {

  @Test
  fun `query is traced`() {
    val response = graphql("""query { greeting(name: "world") }""")

    assertThat(response.has("errors")).isFalse()
    assertThat(response["data"]["greeting"].asText()).isEqualTo("Hello world!")
    awaitTransactionWithSpan("Query.greeting")
  }

  @Test
  fun `resolver error is reported with its transaction`() {
    val response = graphql("""query { greeting(name: "crash") }""")

    assertThat(response["errors"].size()).isEqualTo(1)
    val transaction = awaitTransactionOfError("causing an error for crash")
    assertThat(transaction.hasSpanWithOpAndDescription("graphql", "Query.greeting")).isTrue()
  }

  @Test
  fun `query with a schema mapping is traced`() {
    val response = graphql("""query { project(slug: "proj-slug") { slug status } }""")

    assertThat(response.has("errors")).isFalse()
    assertThat(response["data"]["project"]["slug"].asText()).isEqualTo("proj-slug")
    awaitTransactionWithSpan("Query.project")
  }

  @Test
  fun `mutation is traced`() {
    val response = graphql("""mutation { addProject(slug: "proj-slug") }""")

    assertThat(response.has("errors")).isFalse()
    assertThat(response["data"]["addProject"].asText()).isNotEmpty()
    awaitTransactionWithSpan("Mutation.addProject")
  }

  @Test
  fun `mutation error is reported with its transaction`() {
    val response = graphql("""mutation { addProject(slug: "addprojectcrash") }""")

    assertThat(response["errors"].size()).isEqualTo(1)
    assertThat(response.path("data").path("addProject").asText(null)).isNull()
    val transaction = awaitTransactionOfError("causing a project add error for addprojectcrash")
    assertThat(transaction.hasSpanWithOpAndDescription("graphql", "Mutation.addProject")).isTrue()
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
    assertThat(tasks[0]["id"].asText()).isEqualTo("T1")
    assertThat(tasks[0]["assignee"]["id"].asText()).isEqualTo("A3")
    assertThat(tasks[0]["creator"]["id"].asText()).isEqualTo("C3")

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

  /** Finds the error for [exceptionMessage], then the transaction it belongs to by trace ID. */
  private fun awaitTransactionOfError(exceptionMessage: String): SentryTransaction {
    val error =
      server
        .awaitEnvelope { envelope ->
          envelope.event()?.let { event ->
            event.message?.message?.startsWith("Unresolved RuntimeException for executionId ") ==
              true && event.exceptions?.any { it.value == exceptionMessage } == true
          } == true
        }
        .event()!!
    val traceId = error.contexts.trace!!.traceId
    return server
      .awaitEnvelope { it.transaction()?.contexts?.trace?.traceId == traceId }
      .transaction()!!
  }
}
