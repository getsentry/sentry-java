package io.sentry.protocol

import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SentryLogEvent
import io.sentry.SentryLogEventAttributeValue
import io.sentry.SentryLogEvents
import io.sentry.SentryLogLevel
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class SentryLogsSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      SentryLogEvents(
        listOf(
          SentryLogEvent(
              SentryId("5c1f73d39486827b9e60ceb1fc23277a"),
              DateUtils.dateToSeconds(DateUtils.getDateTime("2004-04-10T18:24:03.000Z")),
              "42e6bd2a-c45e-414d-8066-ed5196fbc686",
              SentryLogLevel.INFO,
            )
            .also {
              it.attributes =
                mutableMapOf(
                  "sentry.sdk.name" to
                    SentryLogEventAttributeValue("string", "sentry.java.spring-boot.jakarta"),
                  "sentry.environment" to SentryLogEventAttributeValue("string", "production"),
                  "sentry.sdk.version" to SentryLogEventAttributeValue("string", "8.11.1"),
                  "sentry.trace.parent_span_id" to
                    SentryLogEventAttributeValue("string", "f28b86350e534671"),
                  "custom.boolean" to SentryLogEventAttributeValue("boolean", true),
                  "custom.double" to SentryLogEventAttributeValue("double", 11.12.toDouble()),
                  "custom.point" to SentryLogEventAttributeValue("string", Point(20, 30)),
                  "custom.integer" to SentryLogEventAttributeValue("integer", 10),
                )
              it.severityNumber = 10
            }
        )
      )
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/sentry_logs.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/sentry_logs.json")
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

  private fun deserialize(json: String): SentryLogEvents {
    val reader = JsonObjectReader(StringReader(json))
    return SentryLogEvents.Deserializer().deserialize(reader, fixture.logger)
  }

  companion object {
    data class Point(val x: Int, val y: Int) {
      override fun toString(): String = "Point{x:$x,y:$y}-Hello"
    }
  }
}
