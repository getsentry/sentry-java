package io.sentry

import com.nhaarman.mockitokotlin2.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

class TraceStateUserSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = TraceState.TraceStateUser(
            "c052c566-6619-45f5-a61f-172802afa39a",
            "f7d8662b-5551-4ef8-b6a8-090f0561a530"
        )
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/trace_state_user.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/trace_state_user.json")
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

    private fun deserialize(json: String): TraceState.TraceStateUser {
        val reader = JsonObjectReader(StringReader(json))
        return TraceState.TraceStateUser.Deserializer().deserialize(reader, fixture.logger)
    }
}
