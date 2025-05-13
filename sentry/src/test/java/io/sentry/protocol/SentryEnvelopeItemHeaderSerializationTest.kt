package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SentryEnvelopeItemHeader
import io.sentry.SentryItemType
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class SentryEnvelopeItemHeaderSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = SentryEnvelopeItemHeader(
            SentryItemType.Event,
            345,
            "5def420f-3dac-4d7b-948b-49de6e551aef",
            "54cf4644-8610-4ff3-a535-34ac1f367501",
            "6f49ad85-a017-4d94-a5d7-6477251da602",
            "android",
            99
        )
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/sentry_envelope_item_header.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/sentry_envelope_item_header.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        assertEquals(expectedJson, actualJson)
    }

    // Helper

    private fun sanitizedFile(path: String): String {
        return FileFromResources.invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")
    }

    private fun serialize(jsonSerializable: JsonSerializable): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        jsonSerializable.serialize(jsonWrt, fixture.logger)
        return wrt.toString()
    }

    private fun deserialize(json: String): SentryEnvelopeItemHeader {
        val reader = JsonObjectReader(StringReader(json))
        return SentryEnvelopeItemHeader.Deserializer().deserialize(reader, fixture.logger)
    }
}
