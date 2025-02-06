package io.sentry

import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonObjectReaderTest {

    class Fixture {
        val logger = mock<ILogger>()
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

    // nextList

    @Test
    fun `returns list of deserializables for list`() {
        val deserializableA = "{\"foo\": \"foo\", \"bar\": \"bar\"}"
        val deserializableB = "{\"foo\": \"fooo\", \"bar\": \"baar\"}"
        val jsonString = "{\"deserializable\": [$deserializableA,$deserializableB]}"
        val reader = fixture.getSut(jsonString)
        val logger = mock<ILogger>()
        reader.beginObject()
        reader.nextName()

        val expected = listOf(
            Deserializable("foo", "bar"),
            Deserializable("fooo", "baar")
        )
        val actual = reader.nextListOrNull(logger, Deserializable.Deserializer())
        assertEquals(expected, actual)
    }

    @Test
    fun `returns empty list for empty list`() {
        val jsonString = "{\"deserializable\": []}"
        val reader = fixture.getSut(jsonString)
        val logger = mock<ILogger>()
        reader.beginObject()
        reader.nextName()

        val expected = emptyList<Deserializable>()
        val actual = reader.nextListOrNull(logger, Deserializable.Deserializer())
        assertEquals(expected, actual)
        verify(fixture.logger, never()).log(any(), any(), any<Throwable>())
    }

    // nextMap

    @Test
    fun `returns null for null map`() {
        val jsonString = "{\"key\": null}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(reader.nextMapOrNull(fixture.logger, Deserializable.Deserializer()))
    }

    @Test
    fun `returns map of deserializables`() {
        val deserializableA = "{\"foo\": \"foo\", \"bar\": \"bar\"}"
        val deserializableB = "{\"foo\": \"fooo\", \"bar\": \"baar\"}"
        val jsonString = "{\"deserializable\": { \"a\":$deserializableA,\"b\":$deserializableB}}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        val expected = mapOf(
            "a" to Deserializable("foo", "bar"),
            "b" to Deserializable("fooo", "baar")
        )
        val actual = reader.nextMapOrNull(fixture.logger, Deserializable.Deserializer())
        assertEquals(expected, actual)
    }

    @Test
    fun `returns empty map`() {
        val jsonString = "{\"deserializable\": {}}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        val expected = mapOf<String, Deserializable>()
        val actual = reader.nextMapOrNull(fixture.logger, Deserializable.Deserializer())
        assertEquals(expected, actual)
        verify(fixture.logger, never()).log(any(), any(), any<Throwable>())
    }

    // nextDateOrNull

    @Test
    fun `returns null for null date`() {
        val jsonString = "{\"key\": null}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(reader.nextDateOrNull(fixture.logger))
    }

    @Test
    fun `returns date for iso date`() {
        val dateIsoFormat = "2000-12-31T23:59:58.000Z"
        val jsonString = "{\"key\": \"${dateIsoFormat}\"}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        val expected = DateUtils.getDateTime(dateIsoFormat)
        val actual = reader.nextDateOrNull(fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun `returns date date for timestamp date`() {
        val dateTimestampFormat = "1581410911"
        val jsonString = "{\"key\": \"${dateTimestampFormat}\"}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        val expected = DateUtils.getDateTimeWithMillisPrecision(dateTimestampFormat)
        val actual = reader.nextDateOrNull(fixture.logger)
        assertEquals(expected, actual)
        verify(fixture.logger, never()).log(any(), any(), any<Throwable>())
    }

    @Test
    fun `returns date for timestamp date with mills precision`() {
        val dateTimestampWithMillis = "1581410911.988"
        val jsonString = "{\"key\": \"${dateTimestampWithMillis}\"}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        val expected = DateUtils.getDateTimeWithMillisPrecision(dateTimestampWithMillis)
        val actual = reader.nextDateOrNull(fixture.logger)
        assertEquals(expected, actual)
        verify(fixture.logger, never()).log(any(), any(), any<Throwable>())
    }

    // nextTimeZoneOrNull

    @Test
    fun `returns null for null timezone`() {
        val jsonString = "{\"key\": null}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertNull(reader.nextTimeZoneOrNull(fixture.logger))
    }

    @Test
    fun `when deserializing a timezone ID string, it should become a Device-TimeZone`() {
        val jsonString = "{\"timezone\": \"Europe/Vienna\"}"
        val reader = fixture.getSut(jsonString)
        reader.beginObject()
        reader.nextName()

        assertEquals("Europe/Vienna", reader.nextTimeZoneOrNull(fixture.logger)?.id)
    }

    data class Deserializable(
        var foo: String? = null,
        var bar: String? = null
    ) {
        class Deserializer : JsonDeserializer<Deserializable> {
            override fun deserialize(reader: ObjectReader, logger: ILogger): Deserializable {
                return Deserializable().apply {
                    reader.beginObject()
                    reader.nextName()
                    foo = reader.nextStringOrNull()
                    reader.nextName()
                    bar = reader.nextStringOrNull()
                    reader.endObject()
                }
            }
        }
    }
}
