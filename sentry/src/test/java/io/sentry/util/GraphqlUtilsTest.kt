package io.sentry.util

import com.google.common.truth.Truth.assertThat
import io.sentry.JsonObjectReader
import io.sentry.SentryOptions
import java.io.StringReader
import kotlin.test.Test

class GraphqlUtilsTest {
  @Test
  fun `filters document from a GraphQL request body`() {
    val options = SentryOptions().also { it.dataCollection.graphql.setDocument(false) }

    val result = GraphqlUtils.filterRequestBody(REQUEST_BODY, options)

    JsonObjectReader(StringReader(result)).use { reader ->
      @Suppress("UNCHECKED_CAST") val body = reader.nextObjectOrNull() as Map<String, Any>
      assertThat(body).containsEntry("operationName", "GetUser")
      assertThat(body).containsEntry("variables", mapOf("id" to "123"))
      assertThat(body).doesNotContainKey("query")
    }
  }

  @Test
  fun `filters variables from a GraphQL request body`() {
    val options = SentryOptions().also { it.dataCollection.graphql.setVariables(false) }

    val result = GraphqlUtils.filterRequestBody(REQUEST_BODY, options)

    JsonObjectReader(StringReader(result)).use { reader ->
      @Suppress("UNCHECKED_CAST") val body = reader.nextObjectOrNull() as Map<String, Any>
      assertThat(body).containsEntry("operationName", "GetUser")
      assertThat(body).containsEntry("query", "query { viewer { name } }")
      assertThat(body).doesNotContainKey("variables")
    }
  }

  @Test
  fun `filters documents from a batched GraphQL request body`() {
    val options = SentryOptions().also { it.dataCollection.graphql.setDocument(false) }

    val result = GraphqlUtils.filterRequestBody(BATCH_REQUEST_BODY, options)

    assertThat(result).isNotNull()
    JsonObjectReader(StringReader(result)).use { reader ->
      @Suppress("UNCHECKED_CAST") val body = reader.nextObjectOrNull() as List<Map<String, Any>>
      assertThat(body).hasSize(2)
      assertThat(body[0]).containsEntry("operationName", "GetUser")
      assertThat(body[0]).containsEntry("variables", mapOf("id" to "123"))
      assertThat(body[0]).doesNotContainKey("query")
      assertThat(body[1]).containsEntry("operationName", "GetTeam")
      assertThat(body[1]).containsEntry("variables", mapOf("slug" to "sdk"))
      assertThat(body[1]).doesNotContainKey("query")
    }
  }

  @Test
  fun `filters variables from a batched GraphQL request body`() {
    val options = SentryOptions().also { it.dataCollection.graphql.setVariables(false) }

    val result = GraphqlUtils.filterRequestBody(BATCH_REQUEST_BODY, options)

    assertThat(result).isNotNull()
    JsonObjectReader(StringReader(result)).use { reader ->
      @Suppress("UNCHECKED_CAST") val body = reader.nextObjectOrNull() as List<Map<String, Any>>
      assertThat(body).hasSize(2)
      assertThat(body[0]).containsEntry("operationName", "GetUser")
      assertThat(body[0]).containsEntry("query", "query { viewer { name } }")
      assertThat(body[0]).doesNotContainKey("variables")
      assertThat(body[1]).containsEntry("operationName", "GetTeam")
      assertThat(body[1]).containsEntry("query", "query { team { name } }")
      assertThat(body[1]).doesNotContainKey("variables")
    }
  }

  private companion object {
    const val REQUEST_BODY =
      """{"operationName":"GetUser","variables":{"id":"123"},"query":"query { viewer { name } }"}"""
    const val BATCH_REQUEST_BODY =
      """[{"operationName":"GetUser","variables":{"id":"123"},"query":"query { viewer { name } }"},{"operationName":"GetTeam","variables":{"slug":"sdk"},"query":"query { team { name } }"}]"""
  }
}
