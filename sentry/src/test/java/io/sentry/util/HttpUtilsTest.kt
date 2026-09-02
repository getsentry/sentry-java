package io.sentry.util

import com.google.common.truth.Truth.assertThat
import io.sentry.KeyValueCollectionBehavior
import java.util.Enumeration
import java.util.StringTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

  @Test
  fun `null enumeration returns null when filtering security cookies from headers`() {
    val enumeration: Enumeration<String>? = null
    val headers = HttpUtils.filterOutSecurityCookiesFromHeader(enumeration, "Cookie", emptyList())

    assertNull(headers)
  }

  @Test
  fun `null list returns null when filtering security cookies from headers`() {
    val list: List<String>? = null
    val headers = HttpUtils.filterOutSecurityCookiesFromHeader(list, "Cookie", emptyList())

    assertNull(headers)
  }

  @Test
  fun `enumeration works when filtering security cookies from headers`() {
    val enumeration: Enumeration<String>? =
      StringTokenizer(
        "Cookie_2=value2; Cookie_3=value3; JSESSIONID=123456789; mysessioncookiename=1F54D793F432FEE4CFC6A3FAED6D062F|Cookie_1=value1; SID=987654312",
        "|",
      )
        as Enumeration<String>
    val headers =
      HttpUtils.filterOutSecurityCookiesFromHeader(
        enumeration,
        "Cookie",
        listOf("mysessioncookiename"),
      )

    assertNotNull(headers)
    assertEquals(2, headers.size)
    assertEquals(
      "Cookie_2=value2; Cookie_3=value3; JSESSIONID=[Filtered]; mysessioncookiename=[Filtered]",
      headers!![0],
    )
    assertEquals("Cookie_1=value1; SID=[Filtered]", headers!![1])
  }

  @Test
  fun `list works when filtering security cookies from headers`() {
    val list: List<String>? =
      listOf(
        "Cookie_2=value2; Cookie_3=value3; JSESSIONID=123456789; mysessioncookiename=1F54D793F432FEE4CFC6A3FAED6D062F",
        "Cookie_1=value1; SID=987654312",
      )
    val headers =
      HttpUtils.filterOutSecurityCookiesFromHeader(list, "Cookie", listOf("mysessioncookiename"))

    assertNotNull(headers)
    assertEquals(2, headers.size)
    assertEquals(
      "Cookie_2=value2; Cookie_3=value3; JSESSIONID=[Filtered]; mysessioncookiename=[Filtered]",
      headers!![0],
    )
    assertEquals("Cookie_1=value1; SID=[Filtered]", headers!![1])
  }

  @Test
  fun `filtering security cookies from header works for corrupted string`() {
    val list: List<String>? = listOf("Cookie_1=value1;; SID=; JSESSIONID; =")
    val headers =
      HttpUtils.filterOutSecurityCookiesFromHeader(list, "Cookie", listOf("mysessioncookiename"))

    assertNotNull(headers)
    assertEquals(1, headers.size)
    assertEquals("Cookie_1=value1;; SID=[Filtered]; JSESSIONID=[Filtered]; =", headers!![0])
  }

  @Test
  fun `filtering security cookies from header works for null string`() {
    val list: List<String?>? = listOf(null)
    val headers =
      HttpUtils.filterOutSecurityCookiesFromHeader(list, "Cookie", listOf("mysessioncookiename"))

    assertNotNull(headers)
    assertEquals(1, headers.size)
    assertEquals(null, headers!![0])
  }
}
