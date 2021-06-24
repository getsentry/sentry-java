package io.sentry.util

import io.sentry.vendor.gson.stream.JsonReader
import org.junit.Test
import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonReaderUtilsTest {

    // nextStringOrNull

    @Test
    fun `returns null for null string`() {
        val jsonString = "{\"key\": null}"
        val reader = reader(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(JsonReaderUtils.nextStringOrNull(reader))
    }

    @Test
    fun `returns string for non-null string`() {
        val jsonString = "{\"key\": \"value\"}"
        val reader = reader(jsonString)
        reader.beginObject()
        reader.nextName()

        assertEquals("value", JsonReaderUtils.nextStringOrNull(reader))
    }

    // nextDoubleOrNull

    @Test
    fun `returns null for null double`() {
        val jsonString = "{\"key\": null}"
        val reader = reader(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(JsonReaderUtils.nextDoubleOrNull(reader))
    }

    @Test
    fun `returns double for non-null double`() {
        val jsonString = "{\"key\": 1.0}"
        val reader = reader(jsonString)
        reader.beginObject()
        reader.nextName()

        assertEquals(1.0, JsonReaderUtils.nextDoubleOrNull(reader))
    }

    // nextLongOrNull

    @Test
    fun `returns null for null long`() {
        val jsonString = "{\"key\": null}"
        val reader = reader(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(JsonReaderUtils.nextLongOrNull(reader))
    }

    @Test
    fun `returns long for non-null long`() {
        val jsonString = "{\"key\": 9223372036854775807}"
        val reader = reader(jsonString)
        reader.beginObject()
        reader.nextName()

        assertEquals(9223372036854775807 , JsonReaderUtils.nextLongOrNull(reader))
    }

    // nextIntegerOrNull

    @Test
    fun `returns null for null integer`() {
        val jsonString = "{\"key\": null}"
        val reader = reader(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(JsonReaderUtils.nextIntegerOrNull(reader))
    }

    @Test
    fun `returns integer for non-null integer`() {
        val jsonString = "{\"key\": 2147483647}"
        val reader = reader(jsonString)
        reader.beginObject()
        reader.nextName()

        assertEquals(2147483647 , JsonReaderUtils.nextIntegerOrNull(reader))
    }

    // nextBooleanOrNull

    @Test
    fun `returns null for null boolean`() {
        val jsonString = "{\"key\": null}"
        val reader = reader(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(JsonReaderUtils.nextBooleanOrNull(reader))
    }

    @Test
    fun `returns boolean for non-null boolean`() {
        val jsonString = "{\"key\": true}"
        val reader = reader(jsonString)
        reader.beginObject()
        reader.nextName()

        assertTrue(JsonReaderUtils.nextBooleanOrNull(reader)!!)
    }

    // Helper

    private fun reader(jsonString: String): JsonReader {
        return JsonReader(StringReader(jsonString))
    }

}
