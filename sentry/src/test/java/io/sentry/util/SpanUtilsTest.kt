package io.sentry.util

import io.sentry.FilterString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpanUtilsTest {

    @Test
    fun `isIgnored returns true for exact match`() {
        val ignoredOrigins = listOf(FilterString("auto.http.spring_jakarta.webmvc"))
        assertTrue(SpanUtils.isIgnored(ignoredOrigins, "auto.http.spring_jakarta.webmvc"))
    }

    @Test
    fun `isIgnored returns true for regex match`() {
        val ignoredOrigins = listOf(FilterString("auto.http.spring.*"))
        assertTrue(SpanUtils.isIgnored(ignoredOrigins, "auto.http.spring_jakarta.webmvc"))
    }

    @Test
    fun `isIgnored returns false for no match`() {
        val ignoredOrigins = listOf(FilterString("auto.http.spring_jakarta.webmvc"))
        assertFalse(SpanUtils.isIgnored(ignoredOrigins, "auto.http.spring.webflux"))
    }

    @Test
    fun `isIgnored returns false for null origin`() {
        val ignoredOrigins = listOf(FilterString("auto.http.spring_jakarta.webmvc"))
        assertFalse(SpanUtils.isIgnored(ignoredOrigins, null))
    }

    @Test
    fun `isIgnored returns false for null ignoredOrigins`() {
        assertFalse(SpanUtils.isIgnored(null, "auto.http.spring_jakarta.webmvc"))
    }

    @Test
    fun `isIgnored returns false for empty ignoredOrigins`() {
        assertFalse(SpanUtils.isIgnored(emptyList(), "auto.http.spring_jakarta.webmvc"))
    }
}
