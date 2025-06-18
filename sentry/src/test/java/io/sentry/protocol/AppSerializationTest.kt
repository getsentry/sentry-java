package io.sentry.protocol

import io.sentry.DateUtils
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

class AppSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            App().apply {
                appIdentifier = "3b7a3313-53b4-43f4-a6a1-7a7c36a9b0db"
                appStartTime = DateUtils.getDateTime("1918-11-17T07:46:04.000Z")
                deviceAppHash = "3d1fcf36-2c25-4378-bdf8-1e65239f1df4"
                buildType = "d78c56cd-eb0f-4213-8899-cd10ddf20763"
                appName = "873656fd-f620-4edf-bb7a-a0d13325dba0"
                appVersion = "801aab22-ad4b-44fb-995c-bacb5387e20c"
                appBuild = "660f0cde-eedb-49dc-a973-8aa1c04f4a28"
                permissions =
                    mapOf(
                        "WRITE_EXTERNAL_STORAGE" to "not_granted",
                        "CAMERA" to "granted",
                    )
                inForeground = true
                viewNames = listOf("MainActivity", "SidebarActivity")
                startType = "cold"
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/app.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/app.json")
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

    private fun deserialize(json: String): App {
        val reader = JsonObjectReader(StringReader(json))
        return App.Deserializer().deserialize(reader, fixture.logger)
    }
}
