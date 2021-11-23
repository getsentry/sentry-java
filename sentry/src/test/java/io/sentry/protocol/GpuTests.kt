package io.sentry.protocol

import com.nhaarman.mockitokotlin2.mock
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class GpuSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = Gpu().apply {
            name = "d623a6b5-e1ab-4402-931b-c06f5a43a5ae"
            id = -596576280
            vendorId = 1874778041
            vendorName = "d732cf76-07dc-48e2-8920-96d6bfc2439d"
            memorySize = -1484004451
            apiType = "95dfc8bc-88ae-4d66-b85f-6c88ad45b80f"
            isMultiThreadedRendering = true
            version = "3f3f73c3-83a2-423a-8a6f-bb3de0d4a6ae"
            npotSupport = "e06b074a-463c-45de-a959-cbabd461d99d"
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/gpu.json")
        val actual = serializeToString(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/gpu.json")
        val actual = deserializeBrowser(expectedJson)
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
        val jsonWrt = JsonObjectWriter(wrt, 100)
        serialize(jsonWrt)
        return wrt.toString()
    }

    private fun deserializeBrowser(json: String): Gpu {
        val reader = JsonObjectReader(StringReader(json))
        return Gpu.Deserializer().deserialize(reader, fixture.logger)
    }
}
