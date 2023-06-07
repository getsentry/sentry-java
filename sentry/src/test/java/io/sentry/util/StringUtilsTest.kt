package io.sentry.util

import org.mockito.kotlin.mock
import java.util.UUID
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

    @Test
    fun `byteCountToString appends B when byte count is in +-1000 range`() {
        assertEquals("500 B", StringUtils.byteCountToString(500))
        assertEquals("-500 B", StringUtils.byteCountToString(-500))
    }

    @Test
    fun `byteCountToString appends kB when byte count is in kilobyte range`() {
        assertEquals("100.5 kB", StringUtils.byteCountToString(100_500))
        assertEquals("-100.5 kB", StringUtils.byteCountToString(-100_500))

        assertEquals("999.0 kB", StringUtils.byteCountToString(999_000))
    }

    @Test
    fun `byteCountToString appends MB when byte count is in megabyte range`() {
        assertEquals("100.1 MB", StringUtils.byteCountToString(100_124_500))
        assertEquals("-100.1 MB", StringUtils.byteCountToString(-100_124_500))

        assertEquals("999.9 MB", StringUtils.byteCountToString(999_945_018))
    }

    @Test
    fun `calculates SHA1 hash of the given string`() {
        val hash = StringUtils.calculateStringHash("http://key@localhost/proj", mock())

        assertEquals("22be31f64088988d5caeb9d19470bc38a0aa0a78", hash)
    }

    @Test
    fun `returns null if the given string is null or empty when calculating hash`() {
        val hashNull = StringUtils.calculateStringHash(null, mock())

        assertNull(hashNull)

        val hashEmpty = StringUtils.calculateStringHash("", mock())

        assertNull(hashEmpty)
    }

    @Test
    fun `returns proper nil UUID if the given string is corrupted`() {
        val normalized = StringUtils.normalizeUUID("0000-0000")
        assertEquals("00000000-0000-0000-0000-000000000000", normalized)
    }

    @Test
    fun `returns the unchanged UUID if it was not corrupted`() {
        val original = UUID.randomUUID().toString()
        val normalized = StringUtils.normalizeUUID(original)
        assertEquals(original, normalized)
    }

    @Test
    fun `joins strings with delimiter`() {
        val result = StringUtils.join(",", listOf("a", "b"))
        assertEquals("a,b", result)
    }

    @Test
    fun `joins single string without delimiter`() {
        val result = StringUtils.join(",", listOf("a"))
        assertEquals("a", result)
    }

    @Test
    fun `joins list string into empty string`() {
        val result = StringUtils.join(",", emptyList())
        assertEquals("", result)
    }
}
