package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterStringTest {
  @Test
  fun `turns string into pattern`() {
    val filterString = FilterString(".*")
    assertTrue(filterString.matches("anything"))
    assertEquals(".*", filterString.filterString)
  }

  @Test
  fun `skips pattern if not a valid regex`() {
    // does not throw if the string is not a valid pattern
    val filterString = FilterString("I love my mustache {")
    assertFalse(filterString.matches("I love my mustache {"))
    assertEquals("I love my mustache {", filterString.filterString)
  }
}
