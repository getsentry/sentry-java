package io.sentry

import kotlin.test.Test
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
}
