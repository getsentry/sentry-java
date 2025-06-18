package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class SentryIdSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = SentryId("afcb46b1140ade5187c4bbb5daa804df")
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expectedJson = sanitizedFile("json/sentry_id.json")
        val actualJson = serialize(fixture.getSut())
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/sentry_id.json")
        val actual = deserialize(expectedJson)
        assertEquals(fixture.getSut(), actual)
    }

    // Helper

    private fun sanitizedFile(path: String): String =
        FileFromResources
            .invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")

    private fun serialize(src: SentryId): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        jsonWrt.beginObject()
        jsonWrt.name("sentry_id")
        src.serialize(jsonWrt, fixture.logger)
        jsonWrt.endObject()
        return wrt.toString()
    }

    private fun deserialize(json: String): SentryId {
        val reader = JsonObjectReader(StringReader(json))
        reader.beginObject()
        reader.nextName()
        return SentryId.Deserializer().deserialize(reader, fixture.logger)
    }
}
