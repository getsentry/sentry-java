package io.sentry.protocol

import com.nhaarman.mockitokotlin2.mock
import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class BrowserSerializationTest {

    private class Fixture {
        var logger: ILogger = mock()

        fun getSut() = Browser().apply {
            name = "e1c723db-7408-4043-baa7-f4e96234e5dc"
            version = "724a48e9-2d35-416b-9f79-132beba2473a"
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("gson/browser.json")
        val actual = serializeToString(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("gson/browser.json")
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

    private fun deserializeApp(json: String): Browser {
        val reader = JsonObjectReader(StringReader(json))
        return Browser.Deserializer().deserialize(reader, fixture.logger)
    }
}
