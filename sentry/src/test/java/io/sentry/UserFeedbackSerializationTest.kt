package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.protocol.SentryId
import java.io.StringReader
import java.io.StringWriter
import java.lang.Exception
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import org.junit.Test

class UserFeedbackSerializationTest {

    private class Fixture {
        var logger: ILogger = mock()

        fun getSut(): UserFeedback {
            val eventId = SentryId("c2fb8fee2e2b49758bcb67cda0f713c7")
            return UserFeedback(eventId).apply {
                name = "John"
                email = "john@me.com"
                comments = "comment"
            }
        }
    }
    private val fixture = Fixture()

    @Test
    fun `serializing user feedback`() {
        val userFeedback = fixture.getSut()
        val actual = serializeToString(userFeedback)

        val expected = "{\"event_id\":\"${userFeedback.eventId}\",\"name\":\"${userFeedback.name}\"," +
            "\"email\":\"${userFeedback.email}\",\"comments\":\"${userFeedback.comments}\"}"

        assertEquals(expected, actual)
    }

    @Test
    fun `serializing unknown`() {
        val userFeedback = fixture.getSut().apply {
            unknown = mapOf(
                "fixture-key" to "fixture-value"
            )
        }
        val actual = serializeToString(userFeedback)
        val expected = "{\"event_id\":\"c2fb8fee2e2b49758bcb67cda0f713c7\"," +
            "\"name\":\"John\",\"email\":\"john@me.com\",\"comments\":\"comment\"," +
            "\"unknown\":{\"fixture-key\":\"fixture-value\"}}"

        assertEquals(expected, actual)
    }

    @Test
    fun `serializing unknown calls json object writer`() {
        val writer: JsonObjectWriter = mock()
        val logger: ILogger = mock()
        val sut = fixture.getSut().apply {
            unknown = mapOf(
                "fixture-key" to "fixture-value"
            )
        }

        sut.serialize(writer, logger)

        verify(writer).name("unknown")
        verify(writer).value(logger, sut.unknown)
    }

    @Test
    fun `deserializing user feedback`() {
        val userFeedback = fixture.getSut()
        val jsonUserFeedback = "{\"event_id\":\"c2fb8fee2e2b49758bcb67cda0f713c7\"," +
            "\"name\":\"John\",\"email\":\"john@me.com\",\"comments\":\"comment\"}"
        val reader = JsonObjectReader(StringReader(jsonUserFeedback))
        val actual = UserFeedback.Deserializer().deserialize(reader, fixture.logger)
        assertNotNull(actual)
        assertEquals(userFeedback.eventId, actual.eventId)
        assertEquals(userFeedback.name, actual.name)
        assertEquals(userFeedback.email, actual.email)
        assertEquals(userFeedback.comments, actual.comments)
    }

    @Test
    fun `deserializing user feedback with missing required fields`() {
        val jsonUserFeedbackWithoutEventId = "{\"name\":\"John\",\"email\":\"john@me.com\"," +
            "\"comments\":\"comment\"}"
        val reader = JsonObjectReader(StringReader(jsonUserFeedbackWithoutEventId))

        try {
            UserFeedback.Deserializer().deserialize(reader, fixture.logger)
            fail()
        } catch (exception: Exception) {
            verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Exception>())
        }
    }

    @Test
    fun `deserializing unknown`() {
        val json = "{\"event_id\":\"c2fb8fee2e2b49758bcb67cda0f713c7\"," +
            "\"name\":\"John\",\"email\":\"john@me.com\",\"comments\":\"comment\"," +
            "\"unknown\":{\"fixture-key\":\"fixture-value\"}}"
        val expected = mapOf(
            "fixture-key" to "fixture-value"
        )

        val reader = JsonObjectReader(StringReader(json))
        val actual = UserFeedback.Deserializer().deserialize(reader, fixture.logger)

        assertEquals(expected, actual.unknown)
    }

    // Helper

    private fun serializeToString(jsonSerializable: JsonSerializable): String {
        return this.serializeToString { wrt -> jsonSerializable.serialize(wrt, fixture.logger) }
    }

    private fun serializeToString(serialize: (JsonObjectWriter) -> Unit): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt)
        serialize(jsonWrt)
        return wrt.toString()
    }
}
