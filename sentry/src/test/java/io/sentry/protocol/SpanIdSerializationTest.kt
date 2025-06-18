package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.SpanId
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class SpanIdSerializationTest {
    private class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = SpanId("bf6b582d-8ce3-412b-a334-f4c5539b9602")
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expectedJson = sanitizedFile("json/span_id.json")
        val actualJson = serialize(fixture.getSut())
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/span_id.json")
        val actual = deserialize(expectedJson)
        assertEquals(fixture.getSut(), actual)
    }

    // Helper

    private fun sanitizedFile(path: String): String =
        FileFromResources
            .invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")

    private fun serialize(src: SpanId): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        jsonWrt.beginObject()
        jsonWrt.name("span_id")
        src.serialize(jsonWrt, fixture.logger)
        jsonWrt.endObject()
        return wrt.toString()
    }

    private fun deserialize(json: String): SpanId {
        val reader = JsonObjectReader(StringReader(json))
        reader.beginObject()
        reader.nextName()
        return SpanId.Deserializer().deserialize(reader, fixture.logger)
    }
}
