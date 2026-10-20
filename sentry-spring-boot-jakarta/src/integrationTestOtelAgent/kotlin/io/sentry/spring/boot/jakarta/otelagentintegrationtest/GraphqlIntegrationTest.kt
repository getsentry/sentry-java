package io.sentry.spring.boot.jakarta.otelagentintegrationtest

import com.fasterxml.jackson.databind.JsonNode
import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.SentryTransaction
import io.sentry.test.event
import io.sentry.test.hasSpanWithOpAndDescription
import io.sentry.test.transaction
import kotlin.test.Test

/**
 * The agent traces each GraphQL operation as one span, with the operation's name as its op and
 * description, so the queries are named.
 */
class GraphqlIntegrationTest : IntegrationTest() {

  @Test
  fun `query is traced`() {
    val response = graphql("""query GreetingQuery { greeting(name: "world") }""")

    assertThat(response.has("errors")).isFalse()
    assertThat(response["data"]["greeting"].asText()).isEqualTo("Hello world!")
    awaitTransactionWithSpan("query GreetingQuery")
  }

  @Test
  fun `resolver error is reported with its transaction`() {
    val response = graphql("""query GreetingQuery { greeting(name: "crash") }""")

    assertThat(response["errors"].size()).isEqualTo(1)
    val transaction = awaitTransactionOfError("causing an error for crash")
    assertThat(transaction.hasOperationSpan("query GreetingQuery")).isTrue()
  }

  @Test
  fun `query with a schema mapping is traced`() {
    val response = graphql("""query ProjectQuery { project(slug: "proj-slug") { slug status } }""")

    assertThat(response.has("errors")).isFalse()
    assertThat(response["data"]["project"]["slug"].asText()).isEqualTo("proj-slug")
    awaitTransactionWithSpan("query ProjectQuery")
  }

  @Test
  fun `mutation is traced`() {
    val response = graphql("""mutation AddProjectMutation { addProject(slug: "proj-slug") }""")

    assertThat(response.has("errors")).isFalse()
    assertThat(response["data"]["addProject"].asText()).isNotEmpty()
    awaitTransactionWithSpan("mutation AddProjectMutation")
  }

  @Test
  fun `mutation error is reported with its transaction`() {
    val response =
      graphql("""mutation AddProjectMutation { addProject(slug: "addprojectcrash") }""")

    assertThat(response["errors"].size()).isEqualTo(1)
    assertThat(response.path("data").path("addProject").asText(null)).isNull()
    val transaction = awaitTransactionOfError("causing a project add error for addprojectcrash")
    assertThat(transaction.hasOperationSpan("mutation AddProjectMutation")).isTrue()
  }

  @Test
  fun `query with batch loaded fields is traced`() {
    val response =
      graphql(
        """query TasksAndAssigneesQuery { tasks(projectSlug: "project-slug") { id assigneeId assignee { id } creatorId creator { id } } }"""
      )

    assertThat(response.has("errors")).isFalse()
    val tasks = response["data"]["tasks"]
    assertThat(tasks.size()).isEqualTo(5)
    assertThat(tasks[0]["id"].asText()).isEqualTo("T1")
    assertThat(tasks[0]["assignee"]["id"].asText()).isEqualTo("A3")
    assertThat(tasks[0]["creator"]["id"].asText()).isEqualTo("C3")

    awaitTransactionWithSpan("query TasksAndAssigneesQuery")
  }

  private fun graphql(query: String): JsonNode {
    val response =
      restTemplate.postForEntity("/graphql", mapOf("query" to query), JsonNode::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)
    return response.body!!
  }

  private fun awaitTransactionWithSpan(operation: String): SentryTransaction =
    server
      .awaitEnvelope {
        it.transaction()?.let { t ->
          t.transaction == "POST /graphql" && t.hasOperationSpan(operation)
        } == true
      }
      .transaction()!!

  private fun SentryTransaction.hasOperationSpan(operation: String): Boolean =
    hasSpanWithOpAndDescription(operation, operation)

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
