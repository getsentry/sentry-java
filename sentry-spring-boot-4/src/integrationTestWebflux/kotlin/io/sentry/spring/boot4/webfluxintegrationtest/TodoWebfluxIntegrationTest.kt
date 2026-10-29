package io.sentry.spring.boot4.webfluxintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.test.hasSpanWithOpAndDescription
import io.sentry.test.transaction
import kotlin.test.Test
import org.springframework.boot.test.web.server.LocalServerPort

class TodoWebfluxIntegrationTest : WebfluxIntegrationTest() {
  @LocalServerPort private var port = 0

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
          "GET http://localhost:$port/stub/todos/1",
        )
      )
      .isTrue()
  }
}
