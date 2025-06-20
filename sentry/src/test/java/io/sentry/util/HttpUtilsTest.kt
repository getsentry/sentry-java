package io.sentry.util

import java.util.Enumeration
import java.util.StringTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HttpUtilsTest {
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
