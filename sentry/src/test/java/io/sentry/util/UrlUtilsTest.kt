package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlUtilsTest {

    @Test
    fun `returns null for null`() {
        assertNull(UrlUtils.parseNullable(null))
    }

    @Test
    fun `strips user info with user and password from http nullable`() {
        val urlDetails = UrlUtils.parseNullable(
            "http://user:password@sentry.io?q=1&s=2&token=secret#top"
        )!!
        assertEquals("http://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
        assertEquals("q=1&s=2&token=secret", urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `strips user info with user and password from http`() {
        val urlDetails = UrlUtils.parse(
            "http://user:password@sentry.io?q=1&s=2&token=secret#top"
        )
        assertEquals("http://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
        assertEquals("q=1&s=2&token=secret", urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `strips user info with user and password from https`() {
        val urlDetails = UrlUtils.parse(
            "https://user:password@sentry.io?q=1&s=2&token=secret#top"
        )
        assertEquals("https://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
        assertEquals("q=1&s=2&token=secret", urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `splits url`() {
        val urlDetails = UrlUtils.parse(
            "https://sentry.io?q=1&s=2&token=secret#top"
        )
        assertEquals("https://sentry.io", urlDetails.url)
        assertEquals("q=1&s=2&token=secret", urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `splits relative url`() {
        val urlDetails = UrlUtils.parse(
            "/users/1?q=1&s=2&token=secret#top"
        )
        assertEquals("/users/1", urlDetails.url)
        assertEquals("q=1&s=2&token=secret", urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `splits relative root url`() {
        val urlDetails = UrlUtils.parse(
            "/?q=1&s=2&token=secret#top"
        )
        assertEquals("/", urlDetails.url)
        assertEquals("q=1&s=2&token=secret", urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `splits url with just query and fragment`() {
        val urlDetails = UrlUtils.parse(
            "/?q=1&s=2&token=secret#top"
        )
        assertEquals("/", urlDetails.url)
        assertEquals("q=1&s=2&token=secret", urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `splits relative url with query only`() {
        val urlDetails = UrlUtils.parse(
            "/users/1?q=1&s=2&token=secret"
        )
        assertEquals("/users/1", urlDetails.url)
        assertEquals("q=1&s=2&token=secret", urlDetails.query)
        assertNull(urlDetails.fragment)
    }

    @Test
    fun `splits relative url with fragment only`() {
        val urlDetails = UrlUtils.parse(
            "/users/1#top"
        )
        assertEquals("/users/1", urlDetails.url)
        assertNull(urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `strips user info with user and password without query`() {
        val urlDetails = UrlUtils.parse(
            "https://user:password@sentry.io#top"
        )
        assertEquals("https://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
        assertNull(urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `splits without query`() {
        val urlDetails = UrlUtils.parse(
            "https://sentry.io#top"
        )
        assertEquals("https://sentry.io", urlDetails.url)
        assertNull(urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `strips user info with user and password without fragment`() {
        val urlDetails = UrlUtils.parse(
            "https://user:password@sentry.io?q=1&s=2&token=secret"
        )
        assertEquals("https://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
        assertEquals("q=1&s=2&token=secret", urlDetails.query)
        assertNull(urlDetails.fragment)
    }

    @Test
    fun `strips user info with user and password without query or fragment`() {
        val urlDetails = UrlUtils.parse(
            "https://user:password@sentry.io"
        )
        assertEquals("https://[Filtered]:[Filtered]@sentry.io", urlDetails.url)
        assertNull(urlDetails.query)
        assertNull(urlDetails.fragment)
    }

    @Test
    fun `splits url without query or fragment and no authority`() {
        val urlDetails = UrlUtils.parse(
            "https://sentry.io"
        )
        assertEquals("https://sentry.io", urlDetails.url)
        assertNull(urlDetails.query)
        assertNull(urlDetails.fragment)
    }

    @Test
    fun `strips user info with user only`() {
        val urlDetails = UrlUtils.parse(
            "https://user@sentry.io?q=1&s=2&token=secret#top"
        )
        assertEquals("https://[Filtered]@sentry.io", urlDetails.url)
        assertEquals("q=1&s=2&token=secret", urlDetails.query)
        assertEquals("top", urlDetails.fragment)
    }

    @Test
    fun `no details extracted with query after fragment`() {
        val urlDetails = UrlUtils.parse(
            "https://user:password@sentry.io#fragment?q=1&s=2&token=secret"
        )
        assertNull(urlDetails.url)
        assertNull(urlDetails.query)
        assertNull(urlDetails.fragment)
    }

    @Test
    fun `no details extracted with query after fragment without authority`() {
        val urlDetails = UrlUtils.parse(
            "https://sentry.io#fragment?q=1&s=2&token=secret"
        )
        assertNull(urlDetails.url)
        assertNull(urlDetails.query)
        assertNull(urlDetails.fragment)
    }

    @Test
    fun `no details extracted from malformed url`() {
        val urlDetails = UrlUtils.parse(
            "htps://user@sentry.io#fragment?q=1&s=2&token=secret"
        )
        assertNull(urlDetails.url)
        assertNull(urlDetails.query)
        assertNull(urlDetails.fragment)
    }
}
