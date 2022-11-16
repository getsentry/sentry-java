package io.sentry.util

import io.sentry.SentryOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlUtilsTest {

    @Test
    fun `returns null for null`() {
        assertNull(UrlUtils.maybeStripSensitiveDataFromUrlNullable(null, SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `keeps sensitive data if sendDefaultPii is true`() {
        assertEquals("https://user:password@sentry.io?q=1&s=2&token=secret#top", UrlUtils.maybeStripSensitiveDataFromUrl("https://user:password@sentry.io?q=1&s=2&token=secret#top", SentryOptions().also { it.isSendDefaultPii = true }))
    }

    @Test
    fun `strips user info with user and password`() {
        assertEquals("http://%s:%s@sentry.io", UrlUtils.maybeStripSensitiveDataFromUrl("http://user:password@sentry.io", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips user info with user and password from https`() {
        assertEquals("https://%s:%s@sentry.io", UrlUtils.maybeStripSensitiveDataFromUrl("https://user:password@sentry.io", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips user info with user only`() {
        assertEquals("http://%s@sentry.io", UrlUtils.maybeStripSensitiveDataFromUrl("http://user@sentry.io", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips user info with user only from https`() {
        assertEquals("https://%s@sentry.io", UrlUtils.maybeStripSensitiveDataFromUrl("https://user@sentry.io", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips token from query params as first param`() {
        assertEquals("https://sentry.io?token=%s", UrlUtils.maybeStripSensitiveDataFromUrl("https://sentry.io?token=secret", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips token from query params as later param`() {
        assertEquals("https://sentry.io?q=%s&s=%s&token=%s", UrlUtils.maybeStripSensitiveDataFromUrl("https://sentry.io?q=1&s=2&token=secret", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips token from query params as first param and keeps anchor`() {
        assertEquals("https://sentry.io?token=%s#top", UrlUtils.maybeStripSensitiveDataFromUrl("https://sentry.io?token=secret#top", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips token from query params as later param and keeps anchor`() {
        assertEquals("https://sentry.io?q=%s&s=%s&token=%s#top", UrlUtils.maybeStripSensitiveDataFromUrl("https://sentry.io?q=1&s=2&token=secret#top", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips token from query params after anchor`() {
        assertEquals("https://api.github.com/users/getsentry/repos/#fragment?token=%s", UrlUtils.maybeStripSensitiveDataFromUrl("https://api.github.com/users/getsentry/repos/#fragment?token=query", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips token from query params after anchor with &`() {
        assertEquals("https://api.github.com/users/getsentry/repos/#fragment?q=%s&token=%s", UrlUtils.maybeStripSensitiveDataFromUrl("https://api.github.com/users/getsentry/repos/#fragment?q=1&token=query", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips query params`() {
        assertEquals("?q=%s&token=%s", UrlUtils.maybeStripSensitiveDataFromQuery("?q=%s&token=query", SentryOptions().also { it.isSendDefaultPii = false }))
    }

    @Test
    fun `strips query params without ?`() {
        assertEquals("q=%s&token=%s", UrlUtils.maybeStripSensitiveDataFromQuery("q=%s&token=query", SentryOptions().also { it.isSendDefaultPii = false }))
    }
}
