package io.sentry.util

import com.google.common.truth.Truth.assertThat
import io.sentry.KeyValueCollectionBehavior
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
          "name=value&access_token=secret&customerId=123",
          KeyValueCollectionBehavior.denyList("customer"),
        )
      )
      .isEqualTo("name=value&access_token=[Filtered]&customerId=[Filtered]")
  }

  @Test
  fun `query parameter allow list only retains allowed non-sensitive values`() {
    assertThat(
        HttpUtils.filterQueryParams(
          "name=value&access_token=secret&customerId=123",
          KeyValueCollectionBehavior.allowList("name", "access_token"),
        )
      )
      .isEqualTo("name=value&access_token=[Filtered]&customerId=[Filtered]")
  }

  @Test
  fun `query parameter filter matches decoded names and preserves encoding`() {
    assertThat(
        HttpUtils.filterQueryParams(
          "access%5Ftoken=secret&display%20name=Jane+Doe",
          KeyValueCollectionBehavior.denyList(),
        )
      )
      .isEqualTo("access%5Ftoken=[Filtered]&display%20name=Jane+Doe")
  }

  @Test
  fun `query parameter filter preserves empty parameters and values`() {
    assertThat(
        HttpUtils.filterQueryParams(
          "name=&flag&&token",
          KeyValueCollectionBehavior.denyList(),
        )
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
          "authorization" to "Bearer token",
          "x-customer" to "customer value",
          "Cookie" to "name=value",
        ),
        KeyValueCollectionBehavior.denyList("customer"),
      )

    assertThat(filtered)
      .containsExactly(
        "content-type",
        "application/json",
        "authorization",
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
          "authorization" to "Bearer token",
          "x-customer" to "customer value",
        ),
        KeyValueCollectionBehavior.allowList("content", "authorization"),
      )

    assertThat(filtered)
      .containsExactly(
        "content-type",
        "application/json",
        "authorization",
        "[Filtered]",
        "x-customer",
        "[Filtered]",
      )
  }
}
