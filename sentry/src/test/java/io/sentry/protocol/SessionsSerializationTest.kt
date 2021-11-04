package io.sentry.protocol

import com.nhaarman.mockitokotlin2.mock
import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.ServerSessionManager
import io.sentry.SessionAggregates
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionsSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = Sessions(
            SessionAggregates("1.0", "prod").apply {
                addSession(DateUtils.getDateTime("2004-11-21T02:06:08.000Z"), ServerSessionManager.Status.Exited)
                addSession(DateUtils.getDateTime("2004-11-21T02:06:12.000Z"), ServerSessionManager.Status.Exited)
                addSession(DateUtils.getDateTime("2004-11-21T02:06:14.000Z"), ServerSessionManager.Status.Crashed)
                addSession(DateUtils.getDateTime("2004-11-21T02:06:15.000Z"), ServerSessionManager.Status.Crashed)
                addSession(DateUtils.getDateTime("2004-11-21T02:07:15.000Z"), ServerSessionManager.Status.Crashed)
                addSession(DateUtils.getDateTime("2004-11-21T02:07:40.000Z"), ServerSessionManager.Status.Errored)
            }
        )
    }
    private val fixture = Fixture()

    @Test
    fun serialization() {
        val expected = sanitizedFile("json/sessions.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/sessions.json")
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

    private fun deserialize(json: String): Sessions {
        val reader = JsonObjectReader(StringReader(json))
        return Sessions.Deserializer().deserialize(reader, fixture.logger)
    }
}
