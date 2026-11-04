package io.sentry.spring.boot.integrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.test.hasSpanWithOpAndDescription
import io.sentry.test.transaction
import kotlin.test.Test

class TodoIntegrationTest : IntegrationTest() {
  @Test
  fun `RestTemplate request is traced`() {
    assertOutgoingRequestIsTraced("/todo/1", "GET /todo/{id}")
  }

  @Test
  fun `WebClient request is traced`() {
    assertOutgoingRequestIsTraced("/todo-webclient/1", "GET /todo-webclient/{id}")
  }

  @Test
  fun `OkHttp request is traced`() {
    assertOutgoingRequestIsTraced("/todo-okhttp/1", "GET /todo-okhttp/{id}")
  }

  private fun assertOutgoingRequestIsTraced(path: String, transactionName: String) {
    val response = restTemplate.getForEntity(path, Todo::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.title).isEqualTo("delectus aut autem")

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == transactionName }.transaction()!!
    assertThat(
        transaction.hasSpanWithOpAndDescription(
          "http.client",
          "GET ${restTemplate.rootUri}/stub/todos/1",
        )
      )
      .isTrue()
  }
}
