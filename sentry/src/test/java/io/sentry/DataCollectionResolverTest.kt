package io.sentry

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class DataCollectionResolverTest {
  @Test
  fun `one resolver is reused per options instance`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver).isSameInstanceAs(options.dataCollectionResolver)
  }

  @Test
  fun `each options instance owns its resolver`() {
    val first = SentryOptions()
    val second = SentryOptions()

    assertThat(first.dataCollectionResolver).isNotSameInstanceAs(second.dataCollectionResolver)
  }

  @Test
  fun `data collection configured reflects namespace explicitness`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver.isDataCollectionConfigured).isFalse()

    options.dataCollection.urlQueryParams = KeyValueCollectionBehavior.denyList()

    assertThat(options.dataCollectionResolver.isDataCollectionConfigured).isTrue()
  }

  @Test
  fun `user info falls back to sendDefaultPii when unset`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver.isUserInfo).isFalse()

    options.isSendDefaultPii = true

    assertThat(options.dataCollectionResolver.isUserInfo).isTrue()
  }

  @Test
  fun `user info uses configured Data Collection value`() {
    val options = SentryOptions().apply { isSendDefaultPii = true }

    options.dataCollection.setUserInfo(false)

    assertThat(options.dataCollectionResolver.isUserInfo).isFalse()

    options.isSendDefaultPii = false
    options.dataCollection.setUserInfo(true)

    assertThat(options.dataCollectionResolver.isUserInfo).isTrue()
  }

  @Test
  fun `user info legacy always variant preserves collection when namespace is absent`() {
    val options = SentryOptions().apply { isSendDefaultPii = false }

    assertThat(options.dataCollectionResolver.isUserInfoWithLegacyAlways).isTrue()

    options.dataCollection.setUserInfo(false)

    assertThat(options.dataCollectionResolver.isUserInfoWithLegacyAlways).isFalse()
  }

  @Test
  fun `omitted booleans use data collection defaults once namespace is explicit`() {
    val options = SentryOptions().apply { isSendDefaultPii = false }

    options.dataCollection.cookies = KeyValueCollectionBehavior.off()

    assertThat(options.dataCollectionResolver.isUserInfo).isTrue()
    assertThat(options.dataCollectionResolver.isDatabaseQueryData).isTrue()
    assertThat(options.dataCollectionResolver.isGraphqlDocument).isTrue()
    assertThat(options.dataCollectionResolver.isGraphqlVariables).isTrue()
  }

  @Test
  fun `database query data uses sendDefaultPii when Data Collection is absent`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver.isDatabaseQueryData).isFalse()

    options.isSendDefaultPii = true

    assertThat(options.dataCollectionResolver.isDatabaseQueryData).isTrue()
  }

  @Test
  fun `database query data uses configured Data Collection value`() {
    val options = SentryOptions().apply { isSendDefaultPii = true }

    options.dataCollection.setDatabaseQueryData(false)

    assertThat(options.dataCollectionResolver.isDatabaseQueryData).isFalse()

    options.isSendDefaultPii = false
    options.dataCollection.setDatabaseQueryData(true)

    assertThat(options.dataCollectionResolver.isDatabaseQueryData).isTrue()
  }

  @Test
  fun `GraphQL document uses sendDefaultPii when Data Collection is absent`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver.isGraphqlDocument).isFalse()

    options.isSendDefaultPii = true

    assertThat(options.dataCollectionResolver.isGraphqlDocument).isTrue()
  }

  @Test
  fun `GraphQL document uses configured Data Collection value`() {
    val options = SentryOptions().apply { isSendDefaultPii = true }

    options.dataCollection.graphql.setDocument(false)

    assertThat(options.dataCollectionResolver.isGraphqlDocument).isFalse()

    options.isSendDefaultPii = false
    options.dataCollection.graphql.setDocument(true)

    assertThat(options.dataCollectionResolver.isGraphqlDocument).isTrue()
  }

  @Test
  fun `GraphQL variables use sendDefaultPii when Data Collection is absent`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver.isGraphqlVariables).isFalse()

    options.isSendDefaultPii = true

    assertThat(options.dataCollectionResolver.isGraphqlVariables).isTrue()
  }

  @Test
  fun `GraphQL variables use configured Data Collection value`() {
    val options = SentryOptions().apply { isSendDefaultPii = true }

    options.dataCollection.graphql.setVariables(false)

    assertThat(options.dataCollectionResolver.isGraphqlVariables).isFalse()

    options.isSendDefaultPii = false
    options.dataCollection.graphql.setVariables(true)

    assertThat(options.dataCollectionResolver.isGraphqlVariables).isTrue()
  }

  @Test
  fun `GraphQL legacy body variants preserve the legacy size gate when namespace is absent`() {
    val options =
      SentryOptions().apply {
        isSendDefaultPii = true
        maxRequestBodySize = SentryOptions.RequestSize.NONE
      }

    assertThat(options.dataCollectionResolver.isGraphqlDocumentWithLegacyBodyGate).isFalse()
    assertThat(options.dataCollectionResolver.isGraphqlVariablesWithLegacyBodyGate).isFalse()

    options.maxRequestBodySize = SentryOptions.RequestSize.SMALL

    assertThat(options.dataCollectionResolver.isGraphqlDocumentWithLegacyBodyGate).isTrue()
    assertThat(options.dataCollectionResolver.isGraphqlVariablesWithLegacyBodyGate).isTrue()
  }

  @Test
  fun `outgoing response legacy body variant preserves the legacy size gate`() {
    val options =
      SentryOptions().apply {
        isSendDefaultPii = true
        maxRequestBodySize = SentryOptions.RequestSize.NONE
      }

    assertThat(options.dataCollectionResolver.isOutgoingResponseBodyWithLegacyBodyGate).isFalse()

    options.maxRequestBodySize = SentryOptions.RequestSize.SMALL

    assertThat(options.dataCollectionResolver.isOutgoingResponseBodyWithLegacyBodyGate).isTrue()
  }

  @Test
  fun `outgoing response legacy body variant uses data collection when namespace is explicit`() {
    val options =
      SentryOptions().apply {
        isSendDefaultPii = false
        maxRequestBodySize = SentryOptions.RequestSize.NONE
        dataCollection.graphql.setDocument(true)
      }

    assertThat(options.dataCollectionResolver.isOutgoingResponseBodyWithLegacyBodyGate).isTrue()

    options.dataCollection.httpBodies = emptySet()

    assertThat(options.dataCollectionResolver.isOutgoingResponseBodyWithLegacyBodyGate).isFalse()
  }

  @Test
  fun `GraphQL legacy body variants ignore the size option when namespace is explicit`() {
    val options =
      SentryOptions().apply {
        isSendDefaultPii = false
        maxRequestBodySize = SentryOptions.RequestSize.NONE
        dataCollection.graphql.setDocument(true)
        dataCollection.graphql.setVariables(true)
      }

    assertThat(options.dataCollectionResolver.isGraphqlDocumentWithLegacyBodyGate).isTrue()
    assertThat(options.dataCollectionResolver.isGraphqlVariablesWithLegacyBodyGate).isTrue()
  }

  @Test
  fun `GraphQL document legacy always variant preserves collection when namespace is absent`() {
    val options = SentryOptions().apply { isSendDefaultPii = false }

    assertThat(options.dataCollectionResolver.isGraphqlDocumentWithLegacyAlways).isTrue()

    options.dataCollection.graphql.setDocument(false)

    assertThat(options.dataCollectionResolver.isGraphqlDocumentWithLegacyAlways).isFalse()
  }

  @Test
  fun `GraphQL variables legacy always variant preserves collection when namespace is absent`() {
    val options = SentryOptions().apply { isSendDefaultPii = false }

    assertThat(options.dataCollectionResolver.isGraphqlVariablesWithLegacyAlways).isTrue()

    options.dataCollection.graphql.setVariables(false)

    assertThat(options.dataCollectionResolver.isGraphqlVariablesWithLegacyAlways).isFalse()
  }

  @Test
  fun `cookies are off when unset and sendDefaultPii is false`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver.cookies).isEqualTo(KeyValueCollectionBehavior.off())
  }

  @Test
  fun `cookies use default deny list when unset and sendDefaultPii is true`() {
    val options = SentryOptions().apply { isSendDefaultPii = true }

    assertThat(options.dataCollectionResolver.cookies)
      .isEqualTo(KeyValueCollectionBehavior.denyList())
  }

  @Test
  fun `cookies use default deny list when namespace is explicit`() {
    val options = SentryOptions().apply { isSendDefaultPii = false }

    options.dataCollection.setUserInfo(false)

    assertThat(options.dataCollectionResolver.cookies)
      .isEqualTo(KeyValueCollectionBehavior.denyList())
  }

  @Test
  fun `cookies use configured Data Collection behavior`() {
    val options = SentryOptions().apply { isSendDefaultPii = false }
    val behavior = KeyValueCollectionBehavior.allowList("language", "theme")

    options.dataCollection.cookies = behavior

    assertThat(options.dataCollectionResolver.cookies).isEqualTo(behavior)
  }

  @Test
  fun `URL query params use default deny list when unset`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver.urlQueryParams)
      .isEqualTo(KeyValueCollectionBehavior.denyList())
  }

  @Test
  fun `URL query params use configured Data Collection behavior`() {
    val options = SentryOptions()
    val behavior = KeyValueCollectionBehavior.allowList("language", "theme")

    options.dataCollection.urlQueryParams = behavior

    assertThat(options.dataCollectionResolver.urlQueryParams).isEqualTo(behavior)
  }

  @Test
  fun `HTTP request headers use default deny list when unset`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver.httpRequestHeaders)
      .isEqualTo(KeyValueCollectionBehavior.denyList())
  }

  @Test
  fun `HTTP request headers use configured Data Collection behavior`() {
    val options = SentryOptions()
    val behavior = KeyValueCollectionBehavior.allowList("content-type")

    options.dataCollection.httpHeaders.request = behavior

    assertThat(options.dataCollectionResolver.httpRequestHeaders).isEqualTo(behavior)
  }

  @Test
  fun `HTTP response headers use default deny list when unset`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver.httpResponseHeaders)
      .isEqualTo(KeyValueCollectionBehavior.denyList())
  }

  @Test
  fun `HTTP response headers use configured Data Collection behavior`() {
    val options = SentryOptions()
    val behavior = KeyValueCollectionBehavior.off()

    options.dataCollection.httpHeaders.response = behavior

    assertThat(options.dataCollectionResolver.httpResponseHeaders).isEqualTo(behavior)
  }

  @Test
  fun `HTTP bodies preserve direction-specific legacy fallbacks when data collection is absent`() {
    val options = SentryOptions()

    assertThat(options.dataCollectionResolver.isIncomingRequestBody).isFalse()
    assertThat(options.dataCollectionResolver.isOutgoingRequestBody).isTrue()
    assertThat(options.dataCollectionResolver.isIncomingResponseBody).isTrue()
    assertThat(options.dataCollectionResolver.isOutgoingResponseBody).isFalse()

    options.isSendDefaultPii = true

    assertThat(options.dataCollectionResolver.isIncomingRequestBody).isTrue()
    assertThat(options.dataCollectionResolver.isOutgoingRequestBody).isTrue()
    assertThat(options.dataCollectionResolver.isIncomingResponseBody).isTrue()
    assertThat(options.dataCollectionResolver.isOutgoingResponseBody).isTrue()
  }

  @Test
  fun `explicit empty data collection enables every HTTP body direction`() {
    val options = SentryOptions().apply { dataCollection = DataCollection() }

    assertThat(options.dataCollectionResolver.isIncomingRequestBody).isTrue()
    assertThat(options.dataCollectionResolver.isOutgoingRequestBody).isTrue()
    assertThat(options.dataCollectionResolver.isIncomingResponseBody).isTrue()
    assertThat(options.dataCollectionResolver.isOutgoingResponseBody).isTrue()
  }

  @Test
  fun `explicit HTTP body set controls every direction`() {
    val options = SentryOptions().apply { isSendDefaultPii = true }

    options.dataCollection.httpBodies =
      setOf(HttpBodyType.INCOMING_REQUEST, HttpBodyType.OUTGOING_RESPONSE)

    assertThat(options.dataCollectionResolver.isIncomingRequestBody).isTrue()
    assertThat(options.dataCollectionResolver.isOutgoingRequestBody).isFalse()
    assertThat(options.dataCollectionResolver.isIncomingResponseBody).isFalse()
    assertThat(options.dataCollectionResolver.isOutgoingResponseBody).isTrue()
  }
}
