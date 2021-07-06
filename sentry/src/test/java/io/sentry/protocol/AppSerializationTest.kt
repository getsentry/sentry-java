package io.sentry.protocol

import com.nhaarman.mockitokotlin2.mock
import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SpanContext
import io.sentry.UserFeedback
import io.sentry.util.RandomObjectFiller
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class AppSerializationTest {

    private class Fixture {
        var logger: ILogger = mock()

        fun getSut() = App().apply {
            appIdentifier = "3b7a3313-53b4-43f4-a6a1-7a7c36a9b0db"
            appStartTime = DateUtils.getDateTime("1918-11-17T07:46:04.000Z")
            deviceAppHash = "3d1fcf36-2c25-4378-bdf8-1e65239f1df4"
            buildType = "d78c56cd-eb0f-4213-8899-cd10ddf20763"
            appName = "873656fd-f620-4edf-bb7a-a0d13325dba0"
            appVersion = "801aab22-ad4b-44fb-995c-bacb5387e20c"
            appBuild = "660f0cde-eedb-49dc-a973-8aa1c04f4a28"
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("gson/app.json")
        val actual = serializeToString(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("gson/app.json")
        val actual = deserializeApp(expectedJson)
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

    private fun deserializeApp(json: String): App {
        val reader = JsonObjectReader(StringReader(json))
        return App.Deserializer().deserialize(reader, fixture.logger)
    }
}
