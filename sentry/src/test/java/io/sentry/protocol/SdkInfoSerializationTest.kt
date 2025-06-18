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

class SdkInfoSerializationTest {
    private class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            SdkInfo().apply {
                sdkName = "182c4407-c1e1-4427-9b5a-ad2e22b1046a"
                versionMajor = 2045114005
                versionMinor = 1436566288
                versionPatchlevel = 1637914973
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/sdk_info.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/sdk_info.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        assertEquals(expectedJson, actualJson)
    }

    // Helper

    private fun sanitizedFile(path: String): String =
        FileFromResources
            .invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")

    private fun serialize(jsonSerializable: JsonSerializable): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        jsonSerializable.serialize(jsonWrt, fixture.logger)
        return wrt.toString()
    }

    private fun deserialize(json: String): SdkInfo {
        val reader = JsonObjectReader(StringReader(json))
        return SdkInfo.Deserializer().deserialize(reader, fixture.logger)
    }
}
