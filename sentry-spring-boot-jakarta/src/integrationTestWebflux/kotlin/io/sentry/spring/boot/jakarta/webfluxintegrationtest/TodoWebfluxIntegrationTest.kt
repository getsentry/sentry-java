package io.sentry.spring.boot.jakarta.webfluxintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.test.hasSpanWithOpAndDescription
import io.sentry.test.transaction
import kotlin.test.Test

class TodoWebfluxIntegrationTest : WebfluxIntegrationTest() {

  @Test
  fun `WebClient request is traced`() {
    val response = restTemplate.getForEntity("/todo-webclient/1", Todo::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.title).isEqualTo("delectus aut autem")

    val transaction =
      server
        .awaitEnvelope { it.transaction()?.transaction == "GET /todo-webclient/{id}" }
        .transaction()!!
    assertThat(
        transaction.hasSpanWithOpAndDescription(
          "http.client",
          "GET ${restTemplate.rootUri}/stub/todos/1",
        )
      )
      .isTrue()
  }
}
