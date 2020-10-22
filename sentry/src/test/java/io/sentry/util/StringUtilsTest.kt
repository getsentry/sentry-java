package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringUtilsTest {
    @Test
    fun `when str has full package, return last string after dot`() {
        assertEquals("TEST", StringUtils.getStringAfterDot("io.sentry.TEST"))
    }

    @Test
    fun `when str is null, return null`() {
        assertNull(StringUtils.getStringAfterDot(null))
    }

    @Test
    fun `when str is empty, return the original str`() {
        assertEquals("", StringUtils.getStringAfterDot(""))
    }

    @Test
    fun `when str ends with a dot, return the original str`() {
        assertEquals("io.sentry.", StringUtils.getStringAfterDot("io.sentry."))
    }

    @Test
    fun `when str has no dots, return the original str`() {
        assertEquals("iosentry", StringUtils.getStringAfterDot("iosentry"))
    }

    @Test
    fun `capitalize string`() {
        assertEquals("Test", StringUtils.capitalize("test"))
    }

    @Test
    fun `capitalize string even if its uppercase`() {
        assertEquals("Test", StringUtils.capitalize("TEST"))
    }

    @Test
    fun `capitalize do not throw if only 1 char`() {
        assertEquals("T", StringUtils.capitalize("t"))
    }

    @Test
    fun `capitalize returns itself if null`() {
        assertNull(StringUtils.capitalize(null))
    }

    @Test
    fun `capitalize returns itself if empty`() {
        assertEquals("", StringUtils.capitalize(""))
    }

    @Test
    fun `removeSurrounding returns null if argument is null`() {
        assertNull(StringUtils.removeSurrounding(null, "\""))
    }

    @Test
    fun `removeSurrounding returns itself if delimiter is null`() {
        assertEquals("test", StringUtils.removeSurrounding("test", null))
    }

    @Test
    fun `removeSurrounding returns itself if first char is different from the last char`() {
        assertEquals("'test$", StringUtils.removeSurrounding("'test$", "'"))
    }

    @Test
    fun `removeSurrounding returns trimmed string if first char is the same as the last char and equal to delimiter`() {
        assertEquals("test", StringUtils.removeSurrounding("\"test\"", "\""))
    }
}
