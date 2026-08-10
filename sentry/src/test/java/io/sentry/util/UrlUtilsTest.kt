package io.sentry.util

import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.ISpan
import io.sentry.KeyValueCollectionBehavior
import io.sentry.SentryOptions
import io.sentry.SpanDataConvention
import io.sentry.protocol.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class UrlUtilsTest {
  @Test
  fun `resolver aware helpers preserve legacy query values`() {
    val resolver = SentryOptions().dataCollectionResolver
    val details = UrlUtils.parse("https://example.com?token=secret", resolver)
    val request = Request()

    details.applyToRequest(request)

    assertThat(request.queryString).isEqualTo("token=secret")
  }

  @Test
  fun `resolver aware helpers filter request span and breadcrumb queries`() {
    val options = SentryOptions().also { it.dataCollection.setUserInfo(false) }
    val details =
      UrlUtils.parse(
        "https://example.com?name=value&token=secret",
        options.dataCollectionResolver,
      )
    val request = Request()
    val span = mock<ISpan>()
    val breadcrumb =
      Breadcrumb.http(
        "https://example.com?name=value&token=secret",
        "GET",
        null,
        options.dataCollectionResolver,
      )

    details.applyToRequest(request)
    details.applyToSpan(span)

    assertThat(request.queryString).isEqualTo("name=value&token=[Filtered]")
    verify(span).setData(SpanDataConvention.HTTP_QUERY_KEY, "name=value&token=[Filtered]")
    assertThat(breadcrumb.getData("http.query")).isEqualTo("name=value&token=[Filtered]")
  }

  @Test
  fun `resolver aware helpers remove query values in off mode`() {
    val options =
      SentryOptions().also { it.dataCollection.urlQueryParams = KeyValueCollectionBehavior.off() }
    val details = UrlUtils.parse("https://example.com?name=value", options.dataCollectionResolver)
    val request = Request()
    val breadcrumb =
      Breadcrumb.http(
        "https://example.com?name=value",
        "GET",
        null,
        options.dataCollectionResolver,
      )

    details.applyToRequest(request)

    assertThat(request.queryString).isNull()
    assertThat(breadcrumb.getData("http.query")).isNull()
  }

  @Test
  fun `returns null for null`() {
    assertNull(UrlUtils.parseNullable(null))
  }

  @Test
  fun `strips user info with user and password from http nullable`() {
    val urlDetails =
      UrlUtils.parseNullable("http://user:password@sentry.io?q=1&s=2&token=secret#top")!!
    assertEquals("http://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
    assertEquals("q=1&s=2&token=secret", urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `strips user info with user and password from http`() {
    val urlDetails = UrlUtils.parse("http://user:password@sentry.io?q=1&s=2&token=secret#top")
    assertEquals("http://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
    assertEquals("q=1&s=2&token=secret", urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `strips user info with user and password from https`() {
    val urlDetails = UrlUtils.parse("https://user:password@sentry.io?q=1&s=2&token=secret#top")
    assertEquals("https://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
    assertEquals("q=1&s=2&token=secret", urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `splits url`() {
    val urlDetails = UrlUtils.parse("https://sentry.io?q=1&s=2&token=secret#top")
    assertEquals("https://sentry.io", urlDetails.url)
    assertEquals("q=1&s=2&token=secret", urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `splits relative url`() {
    val urlDetails = UrlUtils.parse("/users/1?q=1&s=2&token=secret#top")
    assertEquals("/users/1", urlDetails.url)
    assertEquals("q=1&s=2&token=secret", urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `splits relative root url`() {
    val urlDetails = UrlUtils.parse("/?q=1&s=2&token=secret#top")
    assertEquals("/", urlDetails.url)
    assertEquals("q=1&s=2&token=secret", urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `splits url with just query and fragment`() {
    val urlDetails = UrlUtils.parse("/?q=1&s=2&token=secret#top")
    assertEquals("/", urlDetails.url)
    assertEquals("q=1&s=2&token=secret", urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `splits relative url with query only`() {
    val urlDetails = UrlUtils.parse("/users/1?q=1&s=2&token=secret")
    assertEquals("/users/1", urlDetails.url)
    assertEquals("q=1&s=2&token=secret", urlDetails.query)
    assertNull(urlDetails.fragment)
  }

  @Test
  fun `splits relative url with fragment only`() {
    val urlDetails = UrlUtils.parse("/users/1#top")
    assertEquals("/users/1", urlDetails.url)
    assertNull(urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `strips user info with user and password without query`() {
    val urlDetails = UrlUtils.parse("https://user:password@sentry.io#top")
    assertEquals("https://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
    assertNull(urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `splits without query`() {
    val urlDetails = UrlUtils.parse("https://sentry.io#top")
    assertEquals("https://sentry.io", urlDetails.url)
    assertNull(urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `strips user info with user and password without fragment`() {
    val urlDetails = UrlUtils.parse("https://user:password@sentry.io?q=1&s=2&token=secret")
    assertEquals("https://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
    assertEquals("q=1&s=2&token=secret", urlDetails.query)
    assertNull(urlDetails.fragment)
  }

  @Test
  fun `strips user info with user and password without query or fragment`() {
    val urlDetails = UrlUtils.parse("https://user:password@sentry.io")
    assertEquals("https://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
    assertNull(urlDetails.query)
    assertNull(urlDetails.fragment)
  }

  @Test
  fun `splits url without query or fragment and no user info`() {
    val urlDetails = UrlUtils.parse("https://sentry.io")
    assertEquals("https://sentry.io", urlDetails.url)
    assertNull(urlDetails.query)
    assertNull(urlDetails.fragment)
  }

  @Test
  fun `strips user info with user only`() {
    val urlDetails = UrlUtils.parse("https://user@sentry.io?q=1&s=2&token=secret#top")
    assertEquals("https://[Filtered]@sentry.io", urlDetails.url)
    assertEquals("q=1&s=2&token=secret", urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  // Fragment is allowed to contain '?' according to RFC 3986
  @Test
  fun `extracts details with question mark after fragment`() {
    val urlDetails = UrlUtils.parse("https://user:password@sentry.io#fragment?q=1&s=2&token=secret")
    assertEquals("https://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
    assertNull(urlDetails.query)
    assertEquals("fragment?q=1&s=2&token=secret", urlDetails.fragment)
  }

  @Test
  fun `extracts details with question mark after fragment without user info`() {
    val urlDetails = UrlUtils.parse("https://sentry.io#fragment?q=1&s=2&token=secret")
    assertEquals("https://sentry.io", urlDetails.url)
    assertNull(urlDetails.query)
    assertEquals("fragment?q=1&s=2&token=secret", urlDetails.fragment)
  }

  @Test
  fun `no details extracted from malformed url due to invalid protocol`() {
    val urlDetails = UrlUtils.parse("htps://user@sentry.io#fragment?q=1&s=2&token=secret")
    assertNull(urlDetails.url)
    assertNull(urlDetails.query)
    assertNull(urlDetails.fragment)
  }

  @Test
  fun `no details extracted from malformed url due to # symbol in fragment`() {
    val urlDetails = UrlUtils.parse("https://example.com#hello#fragment")
    assertNull(urlDetails.url)
    assertNull(urlDetails.query)
    assertNull(urlDetails.fragment)
  }

  @Test
  fun `strips empty user info`() {
    val urlDetails = UrlUtils.parse("https://@sentry.io?query=a#fragment?q=1&s=2&token=secret")
    assertEquals("https://[Filtered]@sentry.io", urlDetails.url)
    assertEquals("query=a", urlDetails.query)
    assertEquals("fragment?q=1&s=2&token=secret", urlDetails.fragment)
  }

  @Test
  fun `extracts details from relative url with leading @ symbol`() {
    val urlDetails = UrlUtils.parse("@@sentry.io/pages/10?query=a#fragment?q=1&s=2&token=secret")
    assertEquals("@@sentry.io/pages/10", urlDetails.url)
    assertEquals("query=a", urlDetails.query)
    assertEquals("fragment?q=1&s=2&token=secret", urlDetails.fragment)
  }

  @Test
  fun `extracts details from relative url with leading question mark`() {
    val urlDetails = UrlUtils.parse("?query=a#fragment?q=1&s=2&token=secret")
    assertEquals("", urlDetails.url)
    assertEquals("query=a", urlDetails.query)
    assertEquals("fragment?q=1&s=2&token=secret", urlDetails.fragment)
  }

  @Test
  fun `does not filter email address in path`() {
    val urlDetails =
      UrlUtils.parseNullable(
        "https://staging.server.com/api/v4/auth/password/reset/email@example.com"
      )!!
    assertEquals(
      "https://staging.server.com/api/v4/auth/password/reset/email@example.com",
      urlDetails.url,
    )
    assertNull(urlDetails.query)
    assertNull(urlDetails.fragment)
  }

  @Test
  fun `does not filter email address in path with fragment`() {
    val urlDetails =
      UrlUtils.parseNullable(
        "https://staging.server.com/api/v4/auth/password/reset/email@example.com#top"
      )!!
    assertEquals(
      "https://staging.server.com/api/v4/auth/password/reset/email@example.com",
      urlDetails.url,
    )
    assertNull(urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `does not filter email address in path with query and fragment`() {
    val urlDetails =
      UrlUtils.parseNullable(
        "https://staging.server.com/api/v4/auth/password/reset/email@example.com?a=b&c=d#top"
      )!!
    assertEquals(
      "https://staging.server.com/api/v4/auth/password/reset/email@example.com",
      urlDetails.url,
    )
    assertEquals("a=b&c=d", urlDetails.query)
    assertEquals("top", urlDetails.fragment)
  }

  @Test
  fun `does not filter email address in query`() {
    val urlDetails =
      UrlUtils.parseNullable("https://staging.server.com/?email=someone@example.com")!!
    assertEquals("https://staging.server.com/", urlDetails.url)
    assertEquals("email=someone@example.com", urlDetails.query)
  }

  @Test
  fun `does not filter email address in fragment`() {
    val urlDetails =
      UrlUtils.parseNullable("https://staging.server.com#email=someone@example.com")!!
    assertEquals("https://staging.server.com", urlDetails.url)
    assertEquals("email=someone@example.com", urlDetails.fragment)
  }

  @Test
  fun `does not filter email address in fragment with query`() {
    val urlDetails =
      UrlUtils.parseNullable("https://staging.server.com?q=a&b=c#email=someone@example.com")!!
    assertEquals("https://staging.server.com", urlDetails.url)
    assertEquals("q=a&b=c", urlDetails.query)
    assertEquals("email=someone@example.com", urlDetails.fragment)
  }

  @Test
  fun `extracts details from relative url with email in path`() {
    val urlDetails =
      UrlUtils.parse("/emails/user@sentry.io?query=a&b=c#fragment?q=1&s=2&token=secret")
    assertEquals("/emails/user@sentry.io", urlDetails.url)
    assertEquals("query=a&b=c", urlDetails.query)
    assertEquals("fragment?q=1&s=2&token=secret", urlDetails.fragment)
  }

  @Test
  fun `extracts details from relative url with email in query`() {
    val urlDetails =
      UrlUtils.parse("users/10?email=user@sentry.io&b=c#fragment?q=1&s=2&token=secret")
    assertEquals("users/10", urlDetails.url)
    assertEquals("email=user@sentry.io&b=c", urlDetails.query)
    assertEquals("fragment?q=1&s=2&token=secret", urlDetails.fragment)
  }

  @Test
  fun `extracts details from relative url with email in fragment`() {
    val urlDetails =
      UrlUtils.parse("users/10?email=user@sentry.io&b=c#fragment?q=1&s=2&email=user@sentry.io")
    assertEquals("users/10", urlDetails.url)
    assertEquals("email=user@sentry.io&b=c", urlDetails.query)
    assertEquals("fragment?q=1&s=2&email=user@sentry.io", urlDetails.fragment)
  }

  @Test
  fun `extracts path from file url`() {
    val urlDetails = UrlUtils.parse("file:///users/sentry/text.txt")
    assertEquals("file:///users/sentry/text.txt", urlDetails.url)
    assertNull(urlDetails.query)
    assertNull(urlDetails.fragment)
  }

  @Test
  fun `does not extract details from websockets uri`() {
    val urlDetails = UrlUtils.parse("wss://example.com/socket")
    assertNull(urlDetails.url)
    assertNull(urlDetails.query)
    assertNull(urlDetails.fragment)
  }
}
