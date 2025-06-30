package io.sentry.rrweb

import io.sentry.ILogger
import io.sentry.protocol.SerializationUtils
import io.sentry.rrweb.RRWebInteractionEvent.InteractionType.TouchStart
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class RRWebInteractionEventSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      RRWebInteractionEvent().apply {
        timestamp = 12345678901
        id = 1
        x = 1.0f
        y = 2.0f
        interactionType = TouchStart
        pointerId = 1
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = SerializationUtils.sanitizedFile("json/rrweb_interaction_event.json")
    val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = SerializationUtils.sanitizedFile("json/rrweb_interaction_event.json")
    val actual =
      SerializationUtils.deserializeJson(
        expectedJson,
        RRWebInteractionEvent.Deserializer(),
        fixture.logger,
      )
    val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)
    assertEquals(expectedJson, actualJson)
  }
}
