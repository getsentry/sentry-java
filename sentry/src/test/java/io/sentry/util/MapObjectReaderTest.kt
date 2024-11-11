package io.sentry.util

import io.sentry.ILogger
import io.sentry.JsonDeserializer
import io.sentry.JsonSerializable
import io.sentry.NoOpLogger
import io.sentry.ObjectReader
import io.sentry.ObjectWriter
import io.sentry.vendor.gson.stream.JsonToken
import java.math.BigDecimal
import java.net.URI
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class MapObjectReaderTest {

    enum class BasicEnum {
        A
    }

    data class BasicSerializable(var test: String = "string") : JsonSerializable {

        override fun serialize(writer: ObjectWriter, logger: ILogger) {
            writer.beginObject()
                .name("test")
                .value(test)
                .endObject()
        }

        class Deserializer : JsonDeserializer<BasicSerializable> {
            override fun deserialize(reader: ObjectReader, logger: ILogger): BasicSerializable {
                val basicSerializable = BasicSerializable()
                reader.beginObject()
                if (reader.nextName() == "test") {
                    basicSerializable.test = reader.nextString()
                }
                reader.endObject()
                return basicSerializable
            }
        }
    }

    @Test
    fun `deserializes data correctly`() {
        val logger = NoOpLogger.getInstance()
        val data = mutableMapOf<String, Any>()
        val writer = MapObjectWriter(data)

        writer.name("null").nullValue()
        writer.name("int").value(1)
        writer.name("boolean").value(true)
        writer.name("long").value(Long.MAX_VALUE)
        writer.name("double").value(Double.MAX_VALUE)
        writer.name("number").value(BigDecimal(123))
        writer.name("date").value(logger, Date(0))
        writer.name("string").value("string")

        writer.name("TimeZone").value(logger, TimeZone.getTimeZone("Vienna"))
        writer.name("JsonSerializable").value(
            logger,
            BasicSerializable()
        )
        writer.name("Collection").value(logger, listOf("a", "b"))
        writer.name("Arrays").value(logger, arrayOf("b", "c"))
        writer.name("Map").value(logger, mapOf(kotlin.Pair("key", "value")))
        writer.name("MapOfLists").value(logger, mapOf("metric_a" to listOf("foo")))
        writer.name("Locale").value(logger, Locale.US)
        writer.name("URI").value(logger, URI.create("http://www.example.com"))
        writer.name("UUID").value(logger, "00000000-1111-2222-3333-444444444444")
        writer.name("Currency").value(logger, Currency.getInstance("EUR"))
        writer.name("Enum").value(logger, MapObjectWriterTest.BasicEnum.A)
        writer.name("data").value(logger, mapOf("screen" to "MainActivity"))
        writer.name("ListOfObjects").value(logger, listOf(BasicSerializable()))
        writer.name("MapOfObjects").value(logger, mapOf("key" to BasicSerializable()))
        writer.name("MapOfListsObjects").value(logger, mapOf("key" to listOf(BasicSerializable())))

        val reader = MapObjectReader(data)
        reader.beginObject()
        assertEquals(JsonToken.NAME, reader.peek())
        assertEquals("MapOfListsObjects", reader.nextName())
        assertEquals(mapOf("key" to listOf(BasicSerializable())), reader.nextMapOfListOrNull(logger, BasicSerializable.Deserializer()))
        assertEquals("MapOfObjects", reader.nextName())
        assertEquals(mapOf("key" to BasicSerializable()), reader.nextMapOrNull(logger, BasicSerializable.Deserializer()))
        assertEquals("ListOfObjects", reader.nextName())
        assertEquals(listOf(BasicSerializable()), reader.nextListOrNull(logger, BasicSerializable.Deserializer()))
        assertEquals("data", reader.nextName())
        assertEquals(mapOf("screen" to "MainActivity"), reader.nextObjectOrNull())
        assertEquals("Enum", reader.nextName())
        assertEquals(BasicEnum.A, BasicEnum.valueOf(reader.nextString()))
        assertEquals("Currency", reader.nextName())
        assertEquals(Currency.getInstance("EUR"), Currency.getInstance(reader.nextString()))
        assertEquals("UUID", reader.nextName())
        assertEquals(
            "00000000-1111-2222-3333-444444444444",
            reader.nextString()
        )
        assertEquals("URI", reader.nextName())
        assertEquals(URI.create("http://www.example.com"), URI.create(reader.nextString()))
        assertEquals("Locale", reader.nextName())
        assertEquals(Locale.US.toString(), reader.nextString())
        assertEquals("MapOfLists", reader.nextName())
        reader.beginObject()
        assertEquals("metric_a", reader.nextName())
        reader.beginArray()
        assertEquals("foo", reader.nextStringOrNull())
        reader.endArray()
        reader.endObject()
        assertEquals("Map", reader.nextName())
        // nested object
        reader.beginObject()
        assertEquals("key", reader.nextName())
        assertEquals("value", reader.nextStringOrNull())
        reader.endObject()
        assertEquals("Arrays", reader.nextName())
        reader.beginArray()
        assertEquals("b", reader.nextString())
        assertEquals("c", reader.nextString())
        reader.endArray()
        assertEquals("Collection", reader.nextName())
        reader.beginArray()
        assertEquals("a", reader.nextString())
        assertEquals("b", reader.nextString())
        reader.endArray()
        assertEquals("JsonSerializable", reader.nextName())
        assertEquals(BasicSerializable(), reader.nextOrNull(logger, BasicSerializable.Deserializer()))
        assertEquals("TimeZone", reader.nextName())
        assertEquals(TimeZone.getTimeZone("Vienna"), reader.nextTimeZoneOrNull(logger))
        assertEquals("string", reader.nextName())
        assertEquals("string", reader.nextString())
        assertEquals("date", reader.nextName())
        assertEquals(Date(0), reader.nextDateOrNull(logger))
        assertEquals("number", reader.nextName())
        assertEquals(BigDecimal(123), reader.nextObjectOrNull())
        assertEquals("double", reader.nextName())
        assertEquals(Double.MAX_VALUE, reader.nextDoubleOrNull())
        assertEquals("long", reader.nextName())
        assertEquals(Long.MAX_VALUE, reader.nextLongOrNull())
        assertEquals("boolean", reader.nextName())
        assertEquals(true, reader.nextBoolean())
        assertEquals("int", reader.nextName())
        assertEquals(1, reader.nextInt())
        assertEquals("null", reader.nextName())
        reader.nextNull()
        reader.endObject()
    }
}
