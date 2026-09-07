package io.sentry.util

import com.google.common.truth.Truth.assertThat
import io.sentry.KeyValueCollectionBehavior
import java.util.Enumeration
import java.util.StringTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CookieUtilsTest {
  @Test
  fun `cookie filter disables collection in off mode`() {
    assertThat(
        CookieUtils.filterCookies(
          "name=value",
          KeyValueCollectionBehavior.off(),
          emptyList(),
        )
      )
      .isNull()
  }

  @Test
  fun `cookie deny list filters built-in configured and integration sensitive names`() {
    assertThat(
        CookieUtils.filterCookies(
          "name=value; sessionId=secret; customerId=123; frameworkSession=456",
          KeyValueCollectionBehavior.denyList("customer"),
          listOf("frameworkSession"),
        )
      )
      .isEqualTo(
        "name=value; sessionId=[Filtered]; customerId=[Filtered]; frameworkSession=[Filtered]"
      )
  }

  @Test
  fun `cookie allow list only retains allowed non-sensitive values`() {
    assertThat(
        CookieUtils.filterCookies(
          "theme=dark; sessionId=secret; language=en",
          KeyValueCollectionBehavior.allowList("theme", "session"),
          emptyList(),
        )
      )
      .isEqualTo("theme=dark; sessionId=[Filtered]; language=[Filtered]")
  }

  @Test
  fun `cookie filter preserves empty and padded base64 values`() {
    assertThat(
        CookieUtils.filterCookies(
          "empty=; data=YWJjZA==",
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo("empty=; data=YWJjZA==")
  }

  @Test
  fun `cookie filter uses only the first equals separator`() {
    assertThat(
        CookieUtils.filterCookies(
          "theme=dark=contrast; token=abc=123",
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo("theme=dark=contrast; token=[Filtered]")
  }

  @Test
  fun `cookie filter replaces malformed pairs without discarding valid pairs`() {
    assertThat(
        CookieUtils.filterCookies(
          "theme=dark; opaque; =secret; empty=; sessionId=secret",
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo("theme=dark;[Filtered];[Filtered]; empty=; sessionId=[Filtered]")
  }

  @Test
  fun `cookie filter replaces comma-separated malformed cookies`() {
    assertThat(
        CookieUtils.filterCookies(
          "theme=dark, sessionId=secret",
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo("[Filtered]")
  }

  @Test
  fun `cookie filter replaces space-separated malformed cookies`() {
    assertThat(
        CookieUtils.filterCookies(
          "theme=dark sessionId=secret",
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo("[Filtered]")
  }

  @Test
  fun `cookie filter preserves valid names and values`() {
    val cookies =
      "plain=abc123; empty=; base64=YWJjZA==; quoted=\"dark\"; quoted-empty=\"\"; encoded=hello%2Fworld; !#\$%&'*+-.^_`|~=!#\$%&'()*+-./:<=>?@[]^_`{|}~"

    assertThat(
        CookieUtils.filterCookies(
          cookies,
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo(cookies)
  }

  @Test
  fun `cookie filter preserves trailing whitespace after a cookie pair`() {
    assertThat(
        CookieUtils.filterCookies(
          "theme=dark ",
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo("theme=dark ")
  }

  @Test
  fun `cookie filter preserves trailing blank cookie segments`() {
    assertThat(
        CookieUtils.filterCookies(
          "theme=dark;",
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo("theme=dark;")
    assertThat(
        CookieUtils.filterCookies(
          "theme=dark; ",
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo("theme=dark; ")
  }

  @Test
  fun `cookie filter replaces comma-separated malformed cookies in quoted values`() {
    assertThat(
        CookieUtils.filterCookies(
          "theme=\"dark, sessionId=secret\"",
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo("[Filtered]")
  }

  @Test
  fun `cookie filter replaces space-separated malformed cookies in quoted values`() {
    assertThat(
        CookieUtils.filterCookies(
          "theme=\"dark sessionId=secret\"",
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .isEqualTo("[Filtered]")
  }

  @Test
  fun `cookie allow list never exposes malformed pairs`() {
    assertThat(
        CookieUtils.filterCookies(
          "theme=dark; opaque; =secret",
          KeyValueCollectionBehavior.allowList("theme", "opaque"),
          emptyList(),
        )
      )
      .isEqualTo("theme=dark;[Filtered];[Filtered]")
  }

  @Test
  fun `set cookie filter preserves attributes`() {
    assertThat(
        CookieUtils.filterSetCookie(
          "sessionId=secret; Path=/; HttpOnly; SameSite=Lax",
          KeyValueCollectionBehavior.denyList(),
        )
      )
      .isEqualTo("sessionId=[Filtered]; Path=/; HttpOnly; SameSite=Lax")
  }

  @Test
  fun `set cookie filter preserves empty and padded base64 values`() {
    assertThat(
        CookieUtils.filterSetCookie(
          "data=YWJjZA==; Expires=Wed, 09 Jun 2021 10:18:14 GMT; Max-Age=3600; Domain=example.com; Path=/; Secure; HttpOnly; SameSite=Lax",
          KeyValueCollectionBehavior.denyList(),
        )
      )
      .isEqualTo(
        "data=YWJjZA==; Expires=Wed, 09 Jun 2021 10:18:14 GMT; Max-Age=3600; Domain=example.com; Path=/; Secure; HttpOnly; SameSite=Lax"
      )
    assertThat(
        CookieUtils.filterSetCookie(
          "empty=; Path=/",
          KeyValueCollectionBehavior.denyList(),
        )
      )
      .isEqualTo("empty=; Path=/")
  }

  @Test
  fun `set cookie allow list retains allowed non-sensitive value and attributes`() {
    assertThat(
        CookieUtils.filterSetCookie(
          "theme=dark; Path=/; Secure",
          KeyValueCollectionBehavior.allowList("theme"),
        )
      )
      .isEqualTo("theme=dark; Path=/; Secure")
  }

  @Test
  fun `set cookie filter replaces malformed cookie pair and discards attributes`() {
    assertThat(
        CookieUtils.filterSetCookie(
          "opaque; Path=/; HttpOnly",
          KeyValueCollectionBehavior.denyList(),
        )
      )
      .isEqualTo("[Filtered]")
    assertThat(
        CookieUtils.filterSetCookie(
          "=secret; Path=/; HttpOnly",
          KeyValueCollectionBehavior.denyList(),
        )
      )
      .isEqualTo("[Filtered]")
  }

  @Test
  fun `set cookie allow list never exposes malformed cookie pair`() {
    assertThat(
        CookieUtils.filterSetCookie(
          "opaque; Path=/; HttpOnly",
          KeyValueCollectionBehavior.allowList("opaque"),
        )
      )
      .isEqualTo("[Filtered]")
  }

  @Test
  fun `set cookie filter disables collection in off mode`() {
    assertThat(
        CookieUtils.filterSetCookie(
          "theme=dark; Path=/",
          KeyValueCollectionBehavior.off(),
        )
      )
      .isNull()
  }

  @Test
  fun `cookie header filter processes every header value`() {
    assertThat(
        CookieUtils.filterCookiesFromHeader(
          listOf("theme=dark; SID=secret", "language=en"),
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .containsExactly("theme=dark; SID=[Filtered]", "language=en")
      .inOrder()
  }

  @Test
  fun `cookie header filter skips null header values`() {
    assertThat(
        CookieUtils.filterCookiesFromHeader(
          java.util.Arrays.asList("theme=dark", null),
          KeyValueCollectionBehavior.denyList(),
          emptyList(),
        )
      )
      .containsExactly("theme=dark")
  }

  @Test
  fun `null enumeration returns null when filtering security cookies from headers`() {
    val enumeration: Enumeration<String>? = null
    val headers = CookieUtils.filterOutSecurityCookiesFromHeader(enumeration, "Cookie", emptyList())

    assertNull(headers)
  }

  @Test
  fun `null list returns null when filtering security cookies from headers`() {
    val list: List<String>? = null
    val headers = CookieUtils.filterOutSecurityCookiesFromHeader(list, "Cookie", emptyList())

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
      CookieUtils.filterOutSecurityCookiesFromHeader(
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
      CookieUtils.filterOutSecurityCookiesFromHeader(list, "Cookie", listOf("mysessioncookiename"))

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
      CookieUtils.filterOutSecurityCookiesFromHeader(list, "Cookie", listOf("mysessioncookiename"))

    assertNotNull(headers)
    assertEquals(1, headers.size)
    assertEquals("Cookie_1=value1;; SID=[Filtered]; JSESSIONID=[Filtered]; =", headers!![0])
  }

  @Test
  fun `filtering security cookies from header works for null string`() {
    val list: List<String?>? = listOf(null)
    val headers =
      CookieUtils.filterOutSecurityCookiesFromHeader(list, "Cookie", listOf("mysessioncookiename"))

    assertNotNull(headers)
    assertEquals(1, headers.size)
    assertEquals(null, headers!![0])
  }
}
