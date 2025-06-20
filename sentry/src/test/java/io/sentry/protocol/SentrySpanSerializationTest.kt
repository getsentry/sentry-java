package io.sentry.protocol

import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SpanId
import io.sentry.SpanStatus
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class SentrySpanSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      SentrySpan(
        DateUtils.dateToSeconds(DateUtils.getDateTime("1999-11-21T02:06:08.000Z")),
        DateUtils.dateToSeconds(DateUtils.getDateTime("1999-04-10T18:24:03.000Z")),
        SentryId("5b1f73d39486827b9e60ceb1fc23277a"),
        SpanId("4584593a-5d9b-4a55-931f-cfe89c93907d"),
        SpanId("57518091-aed1-47a6-badf-11696035b5f4"),
        "42e6bd1a-c45e-414d-8066-ed5196fbc686",
        "b026345e-ecd1-4555-8d6c-cd6d9f865c89",
        SpanStatus.ALREADY_EXISTS,
        "auto.test.unit.span",
        mapOf("f1333f3a-916a-47b7-8dd6-d6d15fa96e03" to "d4a07684-5b3e-4d08-b605-f9364c398124"),
        mapOf("test_measurement" to MeasurementValue(1, "test")),
        mapOf("518276a7-88d7-408f-ab36-af342f2d7715" to "4a1c2d6c-3f49-41cc-b2ca-d1b36f7ea5a6"),
      )
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/sentry_span.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/sentry_span.json")
    val actual = deserialize(expectedJson)
    val actualJson = serialize(actual)
    assertEquals(expectedJson, actualJson)
  }

  @Test
  fun `deserialize legacy date format`() {
    val expectedJson = sanitizedFile("json/sentry_span_legacy_date_format.json")
    val actual = deserialize(expectedJson)
    val actualJson = serialize(actual)
    assertEquals(sanitizedFile("json/sentry_span.json"), actualJson)
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

  private fun deserialize(json: String): SentrySpan {
    val reader = JsonObjectReader(StringReader(json))
    return SentrySpan.Deserializer().deserialize(reader, fixture.logger)
  }
}
