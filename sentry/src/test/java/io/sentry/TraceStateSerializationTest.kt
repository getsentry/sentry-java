package io.sentry

import com.nhaarman.mockitokotlin2.mock
import io.sentry.protocol.SentryId
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class TraceStateSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = TraceState(
            SentryId("65bcd18546c942069ed957b15b4ace7c"),
            "5d593cac-f833-4845-bb23-4eabdf720da2",
            "9ee2c92c-401e-4296-b6f0-fb3b13edd9ee",
            "0666ab02-6364-4135-aa59-02e8128ce052",
            TraceStateUserSerializationTest.Fixture().getSut(),
            "0252ec25-cd0a-4230-bd2f-936a4585637e"
        )
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/trace_state.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/trace_state.json")
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

    private fun deserialize(json: String): TraceState {
        val reader = JsonObjectReader(StringReader(json))
        return TraceState.Deserializer().deserialize(reader, fixture.logger)
    }
}
