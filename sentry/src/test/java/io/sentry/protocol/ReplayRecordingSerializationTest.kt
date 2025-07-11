package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.ReplayRecording
import io.sentry.protocol.SerializationUtils.deserializeJson
import io.sentry.protocol.SerializationUtils.serializeToString
import io.sentry.rrweb.RRWebBreadcrumbEventSerializationTest
import io.sentry.rrweb.RRWebInteractionEventSerializationTest
import io.sentry.rrweb.RRWebInteractionMoveEventSerializationTest
import io.sentry.rrweb.RRWebMetaEventSerializationTest
import io.sentry.rrweb.RRWebSpanEventSerializationTest
import io.sentry.rrweb.RRWebVideoEventSerializationTest
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class ReplayRecordingSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      ReplayRecording().apply {
        segmentId = 0
        payload =
          listOf(
            RRWebMetaEventSerializationTest.Fixture().getSut(),
            RRWebVideoEventSerializationTest.Fixture().getSut(),
            RRWebBreadcrumbEventSerializationTest.Fixture().getSut(),
            RRWebSpanEventSerializationTest.Fixture().getSut(),
            RRWebInteractionEventSerializationTest.Fixture().getSut(),
            RRWebInteractionMoveEventSerializationTest.Fixture().getSut(),
          )
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = FileFromResources.invoke("json/replay_recording.json").substringBeforeLast("\n")
    val actual = serializeToString(fixture.getSut(), fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson =
      FileFromResources.invoke("json/replay_recording.json").substringBeforeLast("\n")
    val actual = deserializeJson(expectedJson, ReplayRecording.Deserializer(), fixture.logger)
    val actualJson = serializeToString(actual, fixture.logger)
    assertEquals(expectedJson, actualJson)
  }
}
