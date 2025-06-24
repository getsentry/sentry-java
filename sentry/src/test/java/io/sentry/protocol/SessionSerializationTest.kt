package io.sentry.protocol

import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.Session
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class SessionSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      Session(
        Session.State.Crashed,
        DateUtils.getDateTime("1945-06-16T06:36:49.000Z"),
        DateUtils.getDateTime("1970-04-21T09:32:21.000Z"),
        9001,
        "631693c2-3d61-4a93-8fd1-89817426ba5a",
        "3c1ffc32-f68f-4af2-a1ee-dd72f4d62d17",
        true,
        4,
        5.5,
        "5a174e69-a297-4ba4-b6e1-2244a8299ec8",
        "790da4ae-50ca-48a2-98f6-9b7f4e05a8c3",
        "d732be55-b57e-48ec-afe6-b0040c7f93de",
        "b2d0224b-4b1f-49db-94c9-fd4a439b3ef5",
        "anr_foreground",
      )
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/session.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/session.json")
    val actual = deserialize(expectedJson)
    val actualJson = serialize(actual)
    assertEquals(expectedJson, actualJson)
  }

  // Helper

  private fun sanitizedFile(path: String): String =
    FileFromResources.invoke(path).replace(Regex("[\n\r]"), "").replace(" ", "")

  private fun serialize(jsonSerializable: JsonSerializable): String {
    val wrt = StringWriter()
    val jsonWrt = JsonObjectWriter(wrt, 100)
    jsonSerializable.serialize(jsonWrt, fixture.logger)
    return wrt.toString()
  }

  private fun deserialize(json: String): Session {
    val reader = JsonObjectReader(StringReader(json))
    return Session.Deserializer().deserialize(reader, fixture.logger)
  }
}
