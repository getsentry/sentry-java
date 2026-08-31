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

  private companion object {
    const val REQUEST_BODY =
      """{"operationName":"GetUser","variables":{"id":"123"},"query":"query { viewer { name } }"}"""
  }
}
