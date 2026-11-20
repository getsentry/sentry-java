package io.sentry.util

import com.google.common.truth.Truth.assertThat
import io.sentry.KeyValueCollectionBehavior
import java.util.Locale
import kotlin.test.Test

class HttpUtilsTest {
  @Test
  fun `query parameter filter disables collection in off mode`() {
    assertThat(HttpUtils.filterQueryParams("name=value", KeyValueCollectionBehavior.off())).isNull()
  }

  @Test
  fun `query parameter deny list filters built-in sensitive and configured terms`() {
    assertThat(
        HttpUtils.filterQueryParams(
          "name=value&ACCESS_TOKEN=secret&customerId=123",
          KeyValueCollectionBehavior.denyList("CuStOmEr"),
        ),
      )
      .isEqualTo("name=value&ACCESS_TOKEN=[Filtered]&customerId=[Filtered]")
  }

  @Test
  fun `query parameter allow list only retains allowed non-sensitive values`() {
    assertThat(
        HttpUtils.filterQueryParams(
          "name=value&access_token=secret&customerId=123",
          KeyValueCollectionBehavior.allowList("NaMe", "AcCeSs_ToKeN"),
        ),
      )
      .isEqualTo("name=value&access_token=[Filtered]&customerId=[Filtered]")
  }

  @Test
  fun `query parameter filter matches decoded names and preserves encoding`() {
    assertThat(
        HttpUtils.filterQueryParams(
          "access%5Ftoken=secret&display%20name=Jane+Doe",
          KeyValueCollectionBehavior.denyList(),
        ),
      )
      .isEqualTo("access%5Ftoken=[Filtered]&display%20name=Jane+Doe")
  }

  @Test
  fun `query parameter filter preserves empty parameters and values`() {
    assertThat(
        HttpUtils.filterQueryParams(
          "name=&flag&&token",
          KeyValueCollectionBehavior.denyList(),
        ),
      )
      .isEqualTo("name=&flag&&token=[Filtered]")
  }

  @Test
  fun `header filter disables collection in off mode`() {
    val filtered =
      HttpUtils.filterHeaders(
        mapOf("content-type" to "application/json"),
        KeyValueCollectionBehavior.off(),
      )

    assertThat(filtered).isEmpty()
  }

  @Test
  fun `header deny list filters built-in sensitive and configured terms`() {
    val filtered =
      HttpUtils.filterHeaders(
        mapOf(
          "content-type" to "application/json",
          "AUTHORIZATION" to "Bearer token",
          "x-customer" to "customer value",
          "Cookie" to "name=value",
        ),
        KeyValueCollectionBehavior.denyList("CuStOmEr"),
      )

    assertThat(filtered)
      .containsExactly(
        "content-type",
        "application/json",
        "AUTHORIZATION",
        "[Filtered]",
        "x-customer",
        "[Filtered]",
        "Cookie",
        "[Filtered]",
      )
  }

  @Test
  fun `header allow list only retains allowed non-sensitive values`() {
    val filtered =
      HttpUtils.filterHeaders(
        mapOf(
          "content-type" to "application/json",
          "AUTHORIZATION" to "Bearer token",
          "x-customer" to "customer value",
        ),
        KeyValueCollectionBehavior.allowList("CoNtEnT", "AuThOrIzAtIoN"),
      )

    assertThat(filtered)
      .containsExactly(
        "content-type",
        "application/json",
        "AUTHORIZATION",
        "[Filtered]",
        "x-customer",
        "[Filtered]",
      )
  }

  @Test
  fun `sensitive data key check matches keys containing denylist terms in any case`() {
    assertThat(HttpUtils.containsSensitiveDataKey("authToken")).isTrue()
    assertThat(HttpUtils.containsSensitiveDataKey("X-API-KEY")).isTrue()
    assertThat(HttpUtils.containsSensitiveDataKey("SessionId")).isTrue()
  }

  @Test
  fun `sensitive data key check doesn't match keys without denylist terms`() {
    assertThat(HttpUtils.containsSensitiveDataKey("productId")).isFalse()
    assertThat(HttpUtils.containsSensitiveDataKey("query")).isFalse()
  }

  @Test
  fun `containsSensitiveDataKey() - rejects an empty key`() {
    assertThat(HttpUtils.containsSensitiveDataKey("")).isFalse()
  }

  @Test
  fun `containsSensitiveDataKey() - matches independently of the default locale`() {
    val originalLocale = Locale.getDefault()
    try {
      Locale.setDefault(Locale("tr", "TR"))

      assertThat(HttpUtils.containsSensitiveDataKey("SESSION")).isTrue()
      assertThat(HttpUtils.containsSensitiveDataKey("IDENTITY")).isTrue()
    } finally {
      Locale.setDefault(originalLocale)
    }
  }

  @Test
  fun `containsTerm() - matches substrings regardless of key and term case`() {
    assertThat(HttpUtils.containsTerm("X-CUSTOMER-ID", listOf("CuStOmEr"))).isTrue()
    assertThat(HttpUtils.containsTerm("X-CUSTOMER-ID", listOf("account"))).isFalse()
  }

  @Test
  fun `containsTerm() - ignores null and empty terms`() {
    assertThat(HttpUtils.containsTerm("customerId", listOf(null, "", "CuStOmEr"))).isTrue()
    assertThat(HttpUtils.containsTerm("customerId", listOf(null, ""))).isFalse()
  }

  @Test
  fun `containsTerm() - matches no keys when no terms are configured`() {
    assertThat(HttpUtils.containsTerm("customerId", emptyList())).isFalse()
  }

  @Test
  fun `containsTerm() - matches independently of the default locale`() {
    val originalLocale = Locale.getDefault()
    try {
      Locale.setDefault(Locale("tr", "TR"))

      assertThat(HttpUtils.containsTerm("DISPLAY", listOf("display"))).isTrue()
      assertThat(HttpUtils.containsTerm("display", listOf("DISPLAY"))).isTrue()
    } finally {
      Locale.setDefault(originalLocale)
    }
  }
}
