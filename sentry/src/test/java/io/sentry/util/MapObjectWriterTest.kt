package io.sentry.util

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.NoOpLogger
import io.sentry.ObjectWriter
import java.math.BigDecimal
import java.net.Inet4Address
import java.net.URI
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MapObjectWriterTest {

    enum class BasicEnum {
        A
    }

    class BasicSerializable : JsonSerializable {
        override fun serialize(writer: ObjectWriter, logger: ILogger) {
            writer.beginObject()
                .name("key")
                .value("value")
                .endObject()
        }
    }

    @Test
    fun `serializes data correctly`() {
        val logger = NoOpLogger.getInstance()

        val data = mutableMapOf<String, Any>()
        val writer = MapObjectWriter(data)

        writer.name("null").nullValue()
        writer.name("int").value(1 as Int)
        writer.name("boolean").value(true)
        writer.name("long").value(Long.MAX_VALUE)
        writer.name("double").value(Double.MAX_VALUE)
        writer.name("number").value(BigDecimal(123))
        writer.name("date").value(logger, Date(0))
        writer.name("string").value("string")

        writer.name("TimeZone").value(logger, TimeZone.getTimeZone("Vienna"))
        writer.name("JsonSerializable").value(logger, BasicSerializable())
        writer.name("Collection").value(logger, listOf("a", "b"))
        writer.name("Arrays").value(logger, arrayOf("b", "c"))
        writer.name("Map").value(logger, mapOf(kotlin.Pair("key", "value")))
        writer.name("Locale").value(logger, Locale.US)
        writer.name("AtomicIntegerArray").value(logger, AtomicIntegerArray(intArrayOf(0, 1, 2)))
        writer.name("AtomicBoolean").value(logger, AtomicBoolean(false))
        writer.name("URI").value(logger, URI.create("http://www.example.com"))
        writer.name("InetAddress").value(logger, Inet4Address.getByName("1.1.1.1"))
        writer.name("UUID").value(logger, "00000000-1111-2222-3333-444444444444")
        writer.name("Currency").value(logger, Currency.getInstance("EUR"))
        writer.name("Calendar").value(
            logger,
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = 0 }
        )
        writer.name("Enum").value(logger, BasicEnum.A)

        assertEquals(null, data["null"])
        assertEquals(1, data["int"])
        assertEquals(true, data["boolean"])
        assertEquals(Long.MAX_VALUE, data["long"])
        assertEquals(Double.MAX_VALUE, data["double"])
        assertEquals(BigDecimal(123), data["number"])
        assertEquals("1970-01-01T00:00:00.000Z", data["date"])
        assertEquals("string", data["string"])

        assertEquals("GMT", data["TimeZone"])
        assertEquals(
            mapOf(
                kotlin.Pair("key", "value")
            ),
            data["JsonSerializable"]
        )

        assertEquals(listOf("a", "b"), data["Collection"])
        assertEquals(listOf("b", "c"), data["Arrays"])
        assertEquals(mapOf(kotlin.Pair("key", "value")), data["Map"])
        assertEquals("en_US", data["Locale"])
        assertEquals(listOf(0, 1, 2), data["AtomicIntegerArray"])
        assertEquals(false, data["AtomicBoolean"])
        assertEquals("http://www.example.com", data["URI"])
        assertEquals("/1.1.1.1", data["InetAddress"])
        assertEquals("00000000-1111-2222-3333-444444444444", data["UUID"])
        assertEquals("EUR", data["Currency"])
        assertEquals(
            mapOf(
                kotlin.Pair("month", 0),
                kotlin.Pair("year", 1970),
                kotlin.Pair("dayOfMonth", 1),
                kotlin.Pair("hourOfDay", 0),
                kotlin.Pair("minute", 0),
                kotlin.Pair("second", 0)
            ),
            data["Calendar"]
        )
        assertEquals("A", data["Enum"])
    }

    @Test
    fun `serializes objects correctly`() {
        val data = mutableMapOf<String, Any>()
        val writer = MapObjectWriter(data)

        writer.name("object")
        writer.beginObject()
        writer.name("key")
        writer.value("value")
        writer.endObject()

        assertTrue(data.containsKey("object"))
        assertEquals("value", (data["object"] as Map<*, *>)["key"])
    }

    @Test
    fun `serializes nested arrays correctly`() {
        val data = mutableMapOf<String, Any>()
        val writer = MapObjectWriter(data)

        writer.name("array")
        writer.beginArray()

        writer.beginArray()
        writer.value("0")
        writer.value("1")
        writer.endArray()

        writer.value("2")
        writer.endArray()

        assertTrue(data.containsKey("array"))
        assertEquals(2, (data["array"] as List<*>).size)
        assertEquals("0", ((data["array"] as List<*>)[0] as List<*>)[0])
        assertEquals("1", ((data["array"] as List<*>)[0] as List<*>)[1])
        assertEquals("2", (data["array"] as List<*>)[1])
    }

    @Test
    fun `incorrect usage causes exception`() {
        val data = mutableMapOf<String, Any>()
        val writer = MapObjectWriter(data)

        assertFails {
            // missing .beginObject()
            writer.endObject()
        }

        assertFails {
            // missing .beginArray()
            writer.endArray()
        }

        assertFails {
            // missing .name()
            writer.value("value")
        }
    }
}
