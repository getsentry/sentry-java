package io.sentry.spring.boot.jakarta.webfluxintegrationtest

import com.fasterxml.jackson.databind.JsonNode
import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.SentryTransaction
import io.sentry.test.event
import io.sentry.test.hasSpanWithOpAndDescription
import io.sentry.test.transaction
import kotlin.test.Test

class GraphqlWebfluxIntegrationTest : WebfluxIntegrationTest() {

  @Test
  fun `query is traced`() {
    val response = graphql("""query { greeting(name: "world") }""")

    assertThat(response.has("errors")).isFalse()
    assertThat(response["data"]["greeting"].asText()).isEqualTo("Hello world!")
    server.awaitEnvelope {
      it.transaction()?.let { t ->
        t.transaction == "POST /graphql" &&
          t.spans.any { span -> span.op == "graphql" && span.description == "Query.greeting" }
      } == true
    }
  }

  @Test
  fun `resolver error is reported with its transaction`() {
    val response = graphql("""query { greeting(name: "crash") }""")

    assertThat(response["errors"].size()).isEqualTo(1)
    val transaction = awaitTransactionOfError("causing an error for crash")
    assertThat(transaction.hasSpanWithOpAndDescription("graphql", "Query.greeting")).isTrue()
  }

  private fun graphql(query: String): JsonNode {
    val response =
      restTemplate.postForEntity("/graphql", mapOf("query" to query), JsonNode::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)
    return response.body!!
  }

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
