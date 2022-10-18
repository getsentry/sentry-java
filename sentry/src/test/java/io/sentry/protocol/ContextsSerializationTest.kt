package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class ContextsSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = Contexts().apply {
            setApp(AppSerializationTest.Fixture().getSut())
            setBrowser(BrowserSerializationTest.Fixture().getSut())
            setDevice(DeviceSerializationTest.Fixture().getSut())
            setOperatingSystem(OperatingSystemSerializationTest.Fixture().getSut())
            setRuntime(SentryRuntimeSerializationTest.Fixture().getSut())
            setGpu(GpuSerializationTest.Fixture().getSut())
            trace = SpanContextSerializationTest.Fixture().getSut()
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/contexts.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun serializeUnknownEntry() {
        val sut = fixture.getSut()
        sut["fixture-key"] = "fixture-value"

        val writer = mock<JsonObjectWriter>().apply {
            whenever(name(any())).thenReturn(this)
        }
        sut.serialize(writer, fixture.logger)

        verify(writer).name("fixture-key")
        verify(writer).value(fixture.logger, "fixture-value")
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/contexts.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun deserializeUnknownEntry() {
        val sut = fixture.getSut()
        sut["fixture-key"] = "fixture-value"
        val serialized = serialize(sut)
        val deserialized = deserialize(serialized)
        assertEquals("fixture-value", deserialized["fixture-key"])
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

    private fun deserialize(json: String): Contexts {
        val reader = JsonObjectReader(StringReader(json))
        return Contexts.Deserializer().deserialize(reader, fixture.logger)
    }
}
