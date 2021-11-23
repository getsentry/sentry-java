package io.sentry.protocol

import com.nhaarman.mockitokotlin2.mock
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SentryEnvelopeHeader
import io.sentry.TraceStateSerializationTest
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class SentryEnvelopeHeaderSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = SentryEnvelopeHeader(
            SentryIdSerializationTest.Fixture().getSut(),
            SdkVersionSerializationTest.Fixture().getSut(),
            TraceStateSerializationTest.Fixture().getSut()
        )
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/sentry_envelope_header.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/sentry_envelope_header.json")
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

    private fun deserialize(json: String): SentryEnvelopeHeader {
        val reader = JsonObjectReader(StringReader(json))
        return SentryEnvelopeHeader.Deserializer().deserialize(reader, fixture.logger)
    }
}
