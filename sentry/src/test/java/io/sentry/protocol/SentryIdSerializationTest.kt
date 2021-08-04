package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

class SentryIdSerializationTest {

    private class Fixture {
        fun getSut() = SentryId("afcb46b1140ade5187c4bbb5daa804df")
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expectedJson = sanitizedFile("gson/sentry_id.json")
        val actualJson = serialize(fixture.getSut())
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("gson/sentry_id.json")
        val actual = deserialize(expectedJson)
        assertEquals(fixture.getSut(), actual)
    }

    // Helper

    private fun sanitizedFile(path: String): String {
        return FileFromResources.invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")
    }

    private fun serialize(src: SentryId): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt)
        jsonWrt.beginObject()
        jsonWrt.name("sentry_id")
        SentryId.Serializer().serialize(src, jsonWrt)
        jsonWrt.endObject()
        return wrt.toString()
    }

    private fun deserialize(json: String): SentryId {
        val reader = JsonObjectReader(StringReader(json))
        reader.beginObject()
        reader.nextName()
        return SentryId.Deserializer().deserialize(reader)
    }
}
