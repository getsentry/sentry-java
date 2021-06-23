package io.sentry

import io.sentry.json.JsonSerializable
import io.sentry.json.stream.JsonWriter
import io.sentry.protocol.SentryId
import org.junit.Test
import java.io.StringWriter
import kotlin.test.assertEquals

class UserFeedbackSerializationTest {

    private val userFeedback: UserFeedback get() {
        val eventId = SentryId("c2fb8fee2e2b49758bcb67cda0f713c7")
        return UserFeedback(eventId).apply {
            name = "John"
            email = "john@me.com"
            comments = "comment"
        }
    }

    @Test
    fun `serializing user feedback`() {
        val actual = serializeToString(userFeedback)

        val expected = "{\"event_id\":\"${userFeedback.eventId}\",\"name\":\"${userFeedback.name}\"," +
            "\"email\":\"${userFeedback.email}\",\"comments\":\"${userFeedback.comments}\"}"

        assertEquals(expected, actual)
    }

    // Helper

    private fun serializeToString(jsonSerializable: JsonSerializable): String {
        return this.serializeToString { wrt -> jsonSerializable.toJson(wrt) }
    }

    private fun serializeToString(serialize: (JsonWriter) -> Unit): String {
        val wrt = StringWriter()
        val jsonWrt = JsonWriter(wrt);
        serialize(jsonWrt)
        return wrt.toString()
    }
}
