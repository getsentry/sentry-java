package io.sentry

import io.sentry.protocol.SentryId
import java.io.StringReader
import java.io.StringWriter
import java.lang.Exception
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class UserFeedbackSerializationTest {
  private class Fixture {
    val logger = mock<ILogger>()

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
  fun serialize() {
    val userFeedback = fixture.getSut()
    val actual = serialize(userFeedback)

    val expected =
      "{\"event_id\":\"${userFeedback.eventId}\",\"name\":\"${userFeedback.name}\"," +
        "\"email\":\"${userFeedback.email}\",\"comments\":\"${userFeedback.comments}\"}"

    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val userFeedback = fixture.getSut()
    val jsonUserFeedback =
      "{\"event_id\":\"c2fb8fee2e2b49758bcb67cda0f713c7\"," +
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
    val jsonUserFeedbackWithoutEventId =
      "{\"name\":\"John\",\"email\":\"john@me.com\"," + "\"comments\":\"comment\"}"
    val reader = JsonObjectReader(StringReader(jsonUserFeedbackWithoutEventId))

    try {
      UserFeedback.Deserializer().deserialize(reader, fixture.logger)
      fail()
    } catch (exception: Exception) {
      verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Exception>())
    }
  }

  // Helper

  private fun serialize(jsonSerializable: JsonSerializable): String {
    val wrt = StringWriter()
    val jsonWrt = JsonObjectWriter(wrt, 100)
    jsonSerializable.serialize(jsonWrt, fixture.logger)
    return wrt.toString()
  }
}
