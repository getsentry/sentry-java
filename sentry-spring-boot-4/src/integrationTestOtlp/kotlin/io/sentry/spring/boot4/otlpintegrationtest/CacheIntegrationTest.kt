package io.sentry.spring.boot4.otlpintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.test.hasSpanWithOp
import io.sentry.test.transaction
import kotlin.test.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod

class CacheIntegrationTest : IntegrationTest() {

  @Test
  fun `cache put and get produce spans`() {
    val saved = restTemplate.postForEntity("/cache/", Todo(1L, "test-todo"), Todo::class.java)
    assertThat(saved.statusCode.value()).isEqualTo(200)
    assertThat(awaitTransaction("POST /cache/").hasSpanWithOp("cache.put")).isTrue()

    val fetched = restTemplate.getForEntity("/cache/1", Todo::class.java)
    assertThat(fetched.statusCode.value()).isEqualTo(200)
    assertThat(fetched.body!!.title).isEqualTo("test-todo")
    assertThat(awaitTransaction("GET /cache/{id}").hasSpanWithOp("cache.get")).isTrue()
  }

  @Test
  fun `cache evict produces span`() {
    val response =
      restTemplate.exchange("/cache/1", HttpMethod.DELETE, HttpEntity.EMPTY, Void::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)

    assertThat(awaitTransaction("DELETE /cache/{id}").hasSpanWithOp("cache.evict")).isTrue()
  }

  private fun awaitTransaction(name: String) =
    server.awaitEnvelope { it.transaction()?.transaction == name }.transaction()!!
}
