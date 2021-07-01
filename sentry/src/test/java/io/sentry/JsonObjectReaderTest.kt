package io.sentry

import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class JsonObjectReaderTest {

    class Fixture {
        fun getSut(jsonString: String): JsonObjectReader {
            return JsonObjectReader(StringReader(jsonString))
        }
    }
    val fixture = Fixture()

    // nextStringOrNull

    @Test
    fun `returns null for null string`() {
        val jsonString = "{\"key\": null}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(reader.nextStringOrNull())
    }

    @Test
    fun `returns string for non-null string`() {
        val jsonString = "{\"key\": \"value\"}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertEquals("value", reader.nextStringOrNull())
    }

    // nextDoubleOrNull

    @Test
    fun `returns null for null double`() {
        val jsonString = "{\"key\": null}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(reader.nextDoubleOrNull())
    }

    @Test
    fun `returns double for non-null double`() {
        val jsonString = "{\"key\": 1.0}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertEquals(1.0, reader.nextDoubleOrNull())
    }

    // nextLongOrNull

    @Test
    fun `returns null for null long`() {
        val jsonString = "{\"key\": null}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(reader.nextLongOrNull())
    }

    @Test
    fun `returns long for non-null long`() {
        val jsonString = "{\"key\": 9223372036854775807}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertEquals(9223372036854775807, reader.nextLongOrNull())
    }

    // nextIntegerOrNull

    @Test
    fun `returns null for null integer`() {
        val jsonString = "{\"key\": null}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(reader.nextIntegerOrNull())
    }

    @Test
    fun `returns integer for non-null integer`() {
        val jsonString = "{\"key\": 2147483647}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertEquals(2147483647, reader.nextIntegerOrNull())
    }

    // nextBooleanOrNull

    @Test
    fun `returns null for null boolean`() {
        val jsonString = "{\"key\": null}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(reader.nextBooleanOrNull())
    }

    @Test
    fun `returns boolean for non-null boolean`() {
        val jsonString = "{\"key\": true}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertEquals(true, reader.nextBooleanOrNull())
    }
}
