package io.sentry.protocol

import com.nhaarman.mockitokotlin2.mock
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SpanContext
import io.sentry.SpanId
import io.sentry.SpanStatus
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

class SpanContextSerializationTest {

    private class Fixture {
        var logger: ILogger = mock()

        fun getSut() = SpanContext(
            SentryId("afcb46b1140ade5187c4bbb5daa804df"),
            SpanId("bf6b582d-8ce3-412b-a334-f4c5539b9602"),
            "e481581d-35a4-4e97-8a1c-b554bf49f23e",
            SpanId("c7500f2a-d4e6-4f5f-a0f4-6bb67e98d5a2"),
            true
        ).apply {
            description = "c204b6c7-9753-4d45-927d-b19789bfc9a5"
            status = SpanStatus.RESOURCE_EXHAUSTED
            // TODO Generate data with sampled and tags in source
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("gson/span_context.json")
        val actual = serializeToString(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("gson/span_context.json")
        val actual = deserialize(expectedJson)
        val actualJson = serializeToString(actual)
        assertEquals(expectedJson, actualJson)
    }

    // Helper

    private fun sanitizedFile(path: String): String {
        return FileFromResources.invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")
    }

    private fun serializeToString(jsonSerializable: JsonSerializable): String {
        return this.serializeToString { wrt -> jsonSerializable.serialize(wrt, fixture.logger) }
    }

    private fun serializeToString(serialize: (JsonObjectWriter) -> Unit): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt)
        serialize(jsonWrt)
        return wrt.toString()
    }

    private fun deserialize(json: String): SpanContext {
        val reader = JsonObjectReader(StringReader(json))
        return SpanContext.Deserializer().deserialize(reader, fixture.logger)
    }
}
