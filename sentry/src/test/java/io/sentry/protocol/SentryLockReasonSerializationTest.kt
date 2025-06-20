package io.sentry.protocol

import io.sentry.ILogger
import io.sentry.SentryLockReason
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class SentryLockReasonSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      SentryLockReason().apply {
        address = "0x0d3a2f0a"
        type = SentryLockReason.BLOCKED
        threadId = 11
        className = "Object"
        packageName = "java.lang"
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = SerializationUtils.sanitizedFile("json/sentry_lock_reason.json")
    val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)

    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = SerializationUtils.sanitizedFile("json/sentry_lock_reason.json")
    val actual =
      SerializationUtils.deserializeJson<SentryLockReason>(
        expectedJson,
        SentryLockReason.Deserializer(),
        fixture.logger,
      )
    val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)

    assertEquals(expectedJson, actualJson)
  }
}
