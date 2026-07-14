package io.sentry.protocol

import io.sentry.ILogger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock

class ArtContextSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      ArtContext().apply {
        gcTotalCount = 1L
        gcTotalTime = 11.807
        gcBlockingCount = 1L
        gcBlockingTime = 11.873
        gcPreOomeCount = 0L
        gcWaitingTime = 8.054
        freeMemory = 3181568L
        freeMemoryUntilGc = 3181568L
        freeMemoryUntilOome = 196083712L
        totalMemory = 7774208L
        maxMemory = 201326592L
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = SerializationUtils.sanitizedFile("json/art_context.json")
    val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)

    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = SerializationUtils.sanitizedFile("json/art_context.json")
    val actual =
      SerializationUtils.deserializeJson<ArtContext>(
        expectedJson,
        ArtContext.Deserializer(),
        fixture.logger,
      )
    val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)

    assertEquals(expectedJson, actualJson)
  }

  @Test
  fun `deserialize preserves unknown fields`() {
    val jsonWithUnknown =
      SerializationUtils.sanitizedFile("json/art_context.json")
        .removeSuffix("}")
        .plus(",\"new_field\":\"test_value\"}")
    val actual =
      SerializationUtils.deserializeJson<ArtContext>(
        jsonWithUnknown,
        ArtContext.Deserializer(),
        fixture.logger,
      )

    assertNotNull(actual.unknown)
    assertEquals("test_value", actual.unknown!!["new_field"])

    val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)
    assertEquals(jsonWithUnknown, actualJson)
  }
}
