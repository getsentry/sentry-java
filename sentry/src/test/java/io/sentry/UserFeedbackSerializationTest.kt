package io.sentry

import io.sentry.vendor.gson.stream.JsonReader
import io.sentry.vendor.gson.stream.JsonWriter
import io.sentry.protocol.SentryId
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

    @Test
    fun `deserializing user feedback`() {
        val jsonUserFeedback = "{\"event_id\":\"c2fb8fee2e2b49758bcb67cda0f713c7\"," +
            "\"name\":\"John\",\"email\":\"john@me.com\",\"comments\":\"comment\"}"
        val reader = JsonReader(StringReader(jsonUserFeedback))
        val actual = UserFeedback.Deserializer().deserialize(reader)
        assertNotNull(actual)
        assertEquals(userFeedback.eventId, actual.eventId)
        assertEquals(userFeedback.name, actual.name)
        assertEquals(userFeedback.email, actual.email)
        assertEquals(userFeedback.comments, actual.comments)
    }

    // Helper

    private fun serializeToString(jsonSerializable: JsonSerializable): String {
        return this.serializeToString { wrt -> jsonSerializable.serialize(wrt) }
    }

    private fun serializeToString(serialize: (JsonWriter) -> Unit): String {
        val wrt = StringWriter()
        val jsonWrt = JsonWriter(wrt);
        serialize(jsonWrt)
        return wrt.toString()
    }
}
