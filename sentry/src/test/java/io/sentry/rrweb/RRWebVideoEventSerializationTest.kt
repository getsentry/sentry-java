package io.sentry.rrweb

import io.sentry.ILogger
import io.sentry.protocol.SerializationUtils
import io.sentry.rrweb.RRWebEventType.Custom
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class RRWebVideoEventSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = RRWebVideoEvent().apply {
            type = Custom
            timestamp = 12345678901
            tag = "video"
            segmentId = 0
            size = 4_000_000L
            durationMs = 5000
            height = 1920
            width = 1080
            frameCount = 5
            frameRate = 1
            left = 100
            top = 100
        }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = SerializationUtils.sanitizedFile("json/rrweb_video_event.json")
        val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = SerializationUtils.sanitizedFile("json/rrweb_video_event.json")
        val actual =
            SerializationUtils.deserializeJson(expectedJson, RRWebVideoEvent.Deserializer(), fixture.logger)
        val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)
        assertEquals(expectedJson, actualJson)
    }
}
