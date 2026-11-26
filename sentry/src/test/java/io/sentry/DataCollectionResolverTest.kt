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
  fun `booleans use data collection defaults when unset`() {
    val resolver = SentryOptions().dataCollectionResolver

    assertThat(resolver.isUserInfo).isTrue()
    assertThat(resolver.isDatabaseQueryData).isTrue()
    assertThat(resolver.isFilePaths).isTrue()
    assertThat(resolver.isGraphqlDocument).isTrue()
    assertThat(resolver.isGraphqlVariables).isTrue()
  }

  @Test
  fun `booleans use configured data collection values`() {
    val options =
      SentryOptions().apply {
        dataCollection.setUserInfo(false)
        dataCollection.setDatabaseQueryData(false)
        dataCollection.setFilePaths(false)
        dataCollection.graphql.setDocument(false)
        dataCollection.graphql.setVariables(false)
      }

    assertThat(options.dataCollectionResolver.isUserInfo).isFalse()
    assertThat(options.dataCollectionResolver.isDatabaseQueryData).isFalse()
    assertThat(options.dataCollectionResolver.isFilePaths).isFalse()
    assertThat(options.dataCollectionResolver.isGraphqlDocument).isFalse()
    assertThat(options.dataCollectionResolver.isGraphqlVariables).isFalse()
  }

  @Test
  fun `cookies use default deny list when unset`() {
    assertThat(SentryOptions().dataCollectionResolver.cookies)
      .isEqualTo(KeyValueCollectionBehavior.denyList())
  }

  @Test
  fun `cookies use configured data collection behavior`() {
    val options = SentryOptions()
    val behavior = KeyValueCollectionBehavior.allowList("language", "theme")

    options.dataCollection.cookies = behavior

    assertThat(options.dataCollectionResolver.cookies).isEqualTo(behavior)
  }

  @Test
  fun `mutating one fallback key-value behavior does not affect other getters`() {
    val resolver = SentryOptions().dataCollectionResolver

    resolver.cookies.terms = listOf("custom-cookie")

    assertThat(resolver.urlQueryParams.terms).doesNotContain("custom-cookie")
    assertThat(resolver.httpRequestHeaders.terms).doesNotContain("custom-cookie")
    assertThat(resolver.httpResponseHeaders.terms).doesNotContain("custom-cookie")
  }

  @Test
  fun `URL query params use default deny list when unset`() {
    assertThat(SentryOptions().dataCollectionResolver.urlQueryParams)
      .isEqualTo(KeyValueCollectionBehavior.denyList())
  }

  @Test
  fun `URL query params use configured data collection behavior`() {
    val options = SentryOptions()
    val behavior = KeyValueCollectionBehavior.allowList("language", "theme")

    options.dataCollection.urlQueryParams = behavior

    assertThat(options.dataCollectionResolver.urlQueryParams).isEqualTo(behavior)
  }

  @Test
  fun `HTTP request headers use default deny list when unset`() {
    assertThat(SentryOptions().dataCollectionResolver.httpRequestHeaders)
      .isEqualTo(KeyValueCollectionBehavior.denyList())
  }

  @Test
  fun `HTTP request headers use configured data collection behavior`() {
    val options = SentryOptions()
    val behavior = KeyValueCollectionBehavior.allowList("content-type")

    options.dataCollection.httpHeaders.request = behavior

    assertThat(options.dataCollectionResolver.httpRequestHeaders).isEqualTo(behavior)
  }

  @Test
  fun `HTTP response headers use default deny list when unset`() {
    assertThat(SentryOptions().dataCollectionResolver.httpResponseHeaders)
      .isEqualTo(KeyValueCollectionBehavior.denyList())
  }

  @Test
  fun `HTTP response headers use configured data collection behavior`() {
    val options = SentryOptions()
    val behavior = KeyValueCollectionBehavior.off()

    options.dataCollection.httpHeaders.response = behavior

    assertThat(options.dataCollectionResolver.httpResponseHeaders).isEqualTo(behavior)
  }

  @Test
  fun `HTTP bodies use data collection defaults when unset`() {
    val resolver = SentryOptions().dataCollectionResolver

    assertThat(resolver.isIncomingRequestBody).isTrue()
    assertThat(resolver.isOutgoingRequestBody).isTrue()
    assertThat(resolver.isIncomingResponseBody).isTrue()
    assertThat(resolver.isOutgoingResponseBody).isTrue()
  }

  @Test
  fun `explicit HTTP body set controls every direction`() {
    val options = SentryOptions()

    options.dataCollection.httpBodies =
      setOf(HttpBodyType.INCOMING_REQUEST, HttpBodyType.OUTGOING_RESPONSE)

    assertThat(options.dataCollectionResolver.isIncomingRequestBody).isTrue()
    assertThat(options.dataCollectionResolver.isOutgoingRequestBody).isFalse()
    assertThat(options.dataCollectionResolver.isIncomingResponseBody).isFalse()
    assertThat(options.dataCollectionResolver.isOutgoingResponseBody).isTrue()
  }

  @Test
  fun `empty HTTP body set disables every direction`() {
    val options = SentryOptions().apply { dataCollection.httpBodies = emptySet() }

    assertThat(options.dataCollectionResolver.isIncomingRequestBody).isFalse()
    assertThat(options.dataCollectionResolver.isOutgoingRequestBody).isFalse()
    assertThat(options.dataCollectionResolver.isIncomingResponseBody).isFalse()
    assertThat(options.dataCollectionResolver.isOutgoingResponseBody).isFalse()
  }
}
