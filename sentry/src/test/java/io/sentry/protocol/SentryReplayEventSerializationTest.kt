package io.sentry.protocol

import io.sentry.DateUtils
import io.sentry.ILogger
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryReplayEvent
import io.sentry.protocol.SerializationUtils.deserializeJson
import io.sentry.protocol.SerializationUtils.sanitizedFile
import io.sentry.protocol.SerializationUtils.serializeToString
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class SentryReplayEventSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      SentryReplayEvent().apply {
        replayId = SentryId("f715e1d64ef64ea3ad7744b5230813c3")
        segmentId = 0
        timestamp = DateUtils.getDateTime("1942-07-09T12:55:34.000Z")
        replayStartTimestamp = DateUtils.getDateTime("1942-07-09T12:55:34.000Z")
        urls = listOf("ScreenOne")
        errorIds = listOf("ab3a347a4cc14fd4b4cf1dc56b670c5b")
        traceIds = listOf("340cfef948204549ac07c3b353c81c50")
        SentryBaseEventSerializationTest.Fixture().update(this)
        // irrelevant for replay
        serverName = null
        breadcrumbs = null
        extras = null
      }
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
    val expected = sanitizedFile("json/sentry_replay_event.json")
    val actual = serializeToString(fixture.getSut(), fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/sentry_replay_event.json")
    val actual = deserializeJson(expectedJson, SentryReplayEvent.Deserializer(), fixture.logger)
    val actualJson = serializeToString(actual, fixture.logger)
    assertEquals(expectedJson, actualJson)
  }
}
