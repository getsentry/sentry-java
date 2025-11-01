package io.sentry.protocol

import io.sentry.ILogger
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class FeatureFlagsSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() = FeatureFlags(listOf(FeatureFlag("flag-1", true), FeatureFlag("flag-2", false)))
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = SerializationUtils.sanitizedFile("json/feature_flags.json")
    val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)

    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = SerializationUtils.sanitizedFile("json/feature_flags.json")
    val actual =
      SerializationUtils.deserializeJson<FeatureFlags>(
        expectedJson,
        FeatureFlags.Deserializer(),
        fixture.logger,
      )
    val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)

    assertEquals(expectedJson, actualJson)
  }
}
