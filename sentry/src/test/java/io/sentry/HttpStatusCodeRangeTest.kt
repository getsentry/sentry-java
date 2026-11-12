package io.sentry

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpStatusCodeRangeTest {
  @Test
  fun `when single range is given and it is a match`() {
    val range = HttpStatusCodeRange(500)

    assertTrue(range.isInRange(500))
  }

  @Test
  fun `when single range is given and it is not a match`() {
    val range = HttpStatusCodeRange(500)

    assertFalse(range.isInRange(400))
  }

  @Test
  fun `when range is given and it is a match`() {
    val range = HttpStatusCodeRange(500, 599)

    assertTrue(range.isInRange(501))
  }

  @Test
  fun `when range is given and is a match with the lower bound`() {
    val range = HttpStatusCodeRange(500, 599)

    assertTrue(range.isInRange(500))
  }

  @Test
  fun `when range is given and is a match with the upper bound`() {
    val range = HttpStatusCodeRange(500, 599)

    assertTrue(range.isInRange(599))
  }

  @Test
  fun `when range is given and it is lower than min`() {
    val range = HttpStatusCodeRange(500, 599)

    assertFalse(range.isInRange(499))
  }

  @Test
  fun `when range is given and it is higher than max`() {
    val range = HttpStatusCodeRange(500, 599)

    assertFalse(range.isInRange(600))
  }

  @Test
  fun `parses a single status code`() {
    val range = HttpStatusCodeRange.valueOf("404")

    assertTrue(range.isInRange(404))
    assertFalse(range.isInRange(405))
  }

  @Test
  fun `parses an inclusive range with whitespace`() {
    val range = HttpStatusCodeRange.valueOf(" 500 - 599 ")

    assertTrue(range.isInRange(500))
    assertTrue(range.isInRange(599))
    assertFalse(range.isInRange(499))
  }

  @Test
  fun `parses HTTP status boundaries`() {
    assertTrue(HttpStatusCodeRange.valueOf("100-599").isInRange(100))
    assertTrue(HttpStatusCodeRange.valueOf("100-599").isInRange(599))
  }

  @Test
  fun `rejects malformed ranges`() {
    listOf("", "invalid", "+404", "404-", "-404", "400-499-599").forEach {
      assertFailsWith<IllegalArgumentException> { HttpStatusCodeRange.valueOf(it) }
    }
  }

  @Test
  fun `rejects reversed ranges`() {
    assertFailsWith<IllegalArgumentException> { HttpStatusCodeRange.valueOf("500-400") }
  }

  @Test
  fun `rejects status codes outside HTTP range`() {
    listOf("99", "600", "99-200", "500-600").forEach {
      assertFailsWith<IllegalArgumentException> { HttpStatusCodeRange.valueOf(it) }
    }
  }
}
