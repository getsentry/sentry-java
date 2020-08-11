package io.sentry.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class BrowserTest {
    @Test
    fun `cloning browser wont have the same references`() {
        val browser = Browser()
        browser.name = "browser name"
        browser.version = "browser version"
        val unknown = mapOf(Pair("unknown", "unknown"))
        browser.acceptUnknownProperties(unknown)

        val clone = browser.clone()

        assertNotNull(clone)
        assertNotSame(browser, clone)
        assertNotSame(browser.unknown, clone.unknown)
    }

    @Test
    fun `cloning browser will have the same values`() {
        val browser = Browser()
        browser.name = "browser name"
        browser.version = "browser version"
        val unknown = mapOf(Pair("unknown", "unknown"))
        browser.acceptUnknownProperties(unknown)

        val clone = browser.clone()

        assertEquals("browser name", clone.name)
        assertEquals("browser version", clone.version)
        assertEquals("unknown", clone.unknown["unknown"])
    }
}
