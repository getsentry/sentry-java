package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class SentryRuntimeSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            SentryRuntime().apply {
                name = "4ed019c4-9af9-43e0-830e-bfde9fe4461c"
                version = "16534f6b-1670-4bb8-aec2-647a1b97669b"
                rawDescription = "773b5b05-a0f9-4ee6-9f3b-13155c37ad6e"
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/sentry_runtime.json")
        val actual = serializeToString(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/sentry_runtime.json")
        val actual = deserialize(expectedJson)
        val actualJson = serializeToString(actual)
        assertEquals(expectedJson, actualJson)
    }

    // Helper

    private fun sanitizedFile(path: String): String =
        FileFromResources
            .invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")

    private fun serializeToString(jsonSerializable: JsonSerializable): String =
        this.serializeToString { wrt -> jsonSerializable.serialize(wrt, fixture.logger) }

    private fun serializeToString(serialize: (JsonObjectWriter) -> Unit): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        serialize(jsonWrt)
        return wrt.toString()
    }

    private fun deserialize(json: String): SentryRuntime {
        val reader = JsonObjectReader(StringReader(json))
        return SentryRuntime.Deserializer().deserialize(reader, fixture.logger)
    }
}
