package io.sentry.android.core.internal.tombstone

import java.lang.NullPointerException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OleGuidFormatterTest {

  @Test
  fun `a null input string throws`() {
    assertFailsWith<NullPointerException> { OleGuidFormatter.convert(null) }
  }

  @Test
  fun `an input string with fewer than 32 characters throws`() {
    assertFailsWith<IllegalArgumentException> { OleGuidFormatter.convert("abcd") }
  }

  @Test
  fun `an input string with odd number of characters throws`() {
    assertFailsWith<IllegalArgumentException> {
      OleGuidFormatter.convert("0123456789abcdef0123456789abcdef0")
    }
  }

  @Test
  fun `an input example from the develop docs leads to the expected result`() {
    val input = "f1c3bcc0279865fe3058404b2831d9e64135386c"
    val output = OleGuidFormatter.convert(input)
    assertEquals("c0bcc3f1-9827-fe65-3058-404b2831d9e6", output)
  }
}
