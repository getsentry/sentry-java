package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckInUtilsTest {

    @Test
    fun `ignores exact match`() {
        assertTrue(CheckInUtils.isIgnored(listOf("slugA"), "slugA"))
    }

    @Test
    fun `ignores regex match`() {
        assertTrue(CheckInUtils.isIgnored(listOf("slug-.*"), "slug-A"))
    }

    @Test
    fun `does not ignore if ignored list is null`() {
        assertFalse(CheckInUtils.isIgnored(null, "slugA"))
    }

    @Test
    fun `does not ignore if ignored list is empty`() {
        assertFalse(CheckInUtils.isIgnored(emptyList(), "slugA"))
    }

    @Test
    fun `does not ignore if slug is not in ignored list`() {
        assertFalse(CheckInUtils.isIgnored(listOf("slugB"), "slugA"))
    }

    @Test
    fun `does not ignore if slug is does not match ignored list`() {
        assertFalse(CheckInUtils.isIgnored(listOf("slug-.*"), "slugA"))
    }
}
