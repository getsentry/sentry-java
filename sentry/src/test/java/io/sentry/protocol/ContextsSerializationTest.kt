package io.sentry.protocol

import com.nhaarman.mockitokotlin2.mock
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

class ContextsSerializationTest {

    private class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = Contexts().apply {
            setApp(AppSerializationTest.Fixture().getSut())
            setBrowser(BrowserSerializationTest.Fixture().getSut())
            setDevice(DeviceSerializationTest.Fixture().getSut())
            setOperatingSystem(OperatingSystemSerializationTest.Fixture().getSut())
            setRuntime(SentryRuntimeSerializationTest.Fixture().getSut())
            setGpu(GpuSerializationTest.Fixture().getSut())
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("gson/contexts.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("gson/contexts.json")
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
        val jsonWrt = JsonObjectWriter(wrt)
        jsonSerializable.serialize(jsonWrt, fixture.logger)
        return wrt.toString()
    }

    private fun deserialize(json: String): Contexts {
        val reader = JsonObjectReader(StringReader(json))
        return Contexts.Deserializer().deserialize(reader, fixture.logger)
    }
}
