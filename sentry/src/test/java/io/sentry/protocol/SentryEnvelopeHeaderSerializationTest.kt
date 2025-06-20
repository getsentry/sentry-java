package io.sentry.protocol

import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.TraceContextSerializationTest
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class SentryEnvelopeHeaderSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      SentryEnvelopeHeader(
          SentryIdSerializationTest.Fixture().getSut(),
          SdkVersionSerializationTest.Fixture().getSut(),
          TraceContextSerializationTest.Fixture().getSut(),
        )
        .apply { sentAt = DateUtils.getDateTime("2020-02-07T14:16:00.000Z") }
  }

  private val fixture = Fixture()

  @Before
  fun setup() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  @After
  fun teardown() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/sentry_envelope_header.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/sentry_envelope_header.json")
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

  private fun deserialize(json: String): SentryEnvelopeHeader {
    val reader = JsonObjectReader(StringReader(json))
    return SentryEnvelopeHeader.Deserializer().deserialize(reader, fixture.logger)
  }
}
