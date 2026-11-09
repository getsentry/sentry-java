package io.sentry.spring.boot4.otelnoagentintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.SentryTransaction
import io.sentry.test.hasSpanWithOp
import io.sentry.test.hasSpanWithOpAndDescription
import io.sentry.test.transaction
import kotlin.test.Test
import org.springframework.boot.test.web.server.LocalServerPort

class TodoIntegrationTest : IntegrationTest() {
  @LocalServerPort private var port = 0

  @Test
  fun `RestTemplate request is traced inside OpenTelemetry and Sentry spans`() {
    val transaction = fetchTodo("/todo/1", "GET /todo/{id}")
    assertThat(transaction.hasSpanWithOp("todoSpanOtelApi")).isTrue()
    assertThat(transaction.hasSpanWithOp("todoSpanSentryApi")).isTrue()
  }

  @Test
  fun `WebClient request is traced`() {
    fetchTodo("/todo-webclient/1", "GET /todo-webclient/{id}")
  }

  @Test
  fun `RestClient request is traced inside OpenTelemetry and Sentry spans`() {
    val transaction = fetchTodo("/todo-restclient/1", "GET /todo-restclient/{id}")
    assertThat(transaction.hasSpanWithOp("todoRestClientSpanOtelApi")).isTrue()
    assertThat(transaction.hasSpanWithOp("todoRestClientSpanSentryApi")).isTrue()
  }

  /** Fetches a todo through [path] and returns the request's transaction, with its client span. */
  private fun fetchTodo(path: String, transactionName: String): SentryTransaction {
    val response = restTemplate.getForEntity(path, Todo::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.title).isEqualTo("delectus aut autem")

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == transactionName }.transaction()!!
    assertThat(
        transaction.hasSpanWithOpAndDescription(
          "http.client",
          "GET http://localhost:$port/stub/todos/1",
        )
      )
      .isTrue()
    return transaction
  }
}
