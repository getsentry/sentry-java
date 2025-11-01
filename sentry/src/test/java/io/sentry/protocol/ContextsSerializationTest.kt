package io.sentry.protocol

import io.sentry.ILogger
import io.sentry.JsonObjectWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContextsSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      Contexts().apply {
        setApp(AppSerializationTest.Fixture().getSut())
        setBrowser(BrowserSerializationTest.Fixture().getSut())
        setDevice(DeviceSerializationTest.Fixture().getSut())
        setOperatingSystem(OperatingSystemSerializationTest.Fixture().getSut())
        setRuntime(SentryRuntimeSerializationTest.Fixture().getSut())
        setGpu(GpuSerializationTest.Fixture().getSut())
        setFeedback(FeedbackTest.Fixture().getSut())
        setResponse(ResponseSerializationTest.Fixture().getSut())
        setTrace(SpanContextSerializationTest.Fixture().getSut())
        setSpring(SpringSerializationTest.Fixture().getSut())
        setFeatureFlags(FeatureFlagsSerializationTest.Fixture().getSut())
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = SerializationUtils.sanitizedFile("json/contexts.json")
    val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)

    assertEquals(expected, actual)
  }

  @Test
  fun serializeUnknownEntry() {
    val sut = fixture.getSut()
    sut["fixture-key"] = "fixture-value"

    val writer = mock<JsonObjectWriter>().apply { whenever(name(any())).thenReturn(this) }
    sut.serialize(writer, fixture.logger)

    verify(writer).name("fixture-key")
    verify(writer).value(fixture.logger, "fixture-value")
  }

  @Test
  fun deserialize() {
    val expectedJson = SerializationUtils.sanitizedFile("json/contexts.json")
    val actual =
      SerializationUtils.deserializeJson(expectedJson, Contexts.Deserializer(), fixture.logger)
    val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)

    assertEquals(expectedJson, actualJson)
  }

  @Test
  fun deserializeUnknownEntry() {
    val sut = fixture.getSut()
    sut["fixture-key"] = "fixture-value"
    val serialized = SerializationUtils.serializeToString(sut, fixture.logger)
    val deserialized =
      SerializationUtils.deserializeJson(serialized, Contexts.Deserializer(), fixture.logger)

    assertEquals("fixture-value", deserialized["fixture-key"])
  }
}
