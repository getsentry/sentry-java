package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class BrowserTest {
  @Test
  fun `copying browser wont have the same references`() {
    val browser = Browser()
    browser.name = "browser name"
    browser.version = "browser version"
    val unknown = mapOf(Pair("unknown", "unknown"))
    browser.setUnknown(unknown)

    val clone = Browser(browser)

    assertNotNull(clone)
    assertNotSame(browser, clone)
    assertNotSame(browser.unknown, clone.unknown)
  }

  @Test
  fun `copying browser will have the same values`() {
    val browser = Browser()
    browser.name = "browser name"
    browser.version = "browser version"
    val unknown = mapOf(Pair("unknown", "unknown"))
    browser.setUnknown(unknown)

    val clone = Browser(browser)

    assertEquals("browser name", clone.name)
    assertEquals("browser version", clone.version)
    assertNotNull(clone.unknown) { assertEquals("unknown", it["unknown"]) }
  }
}
