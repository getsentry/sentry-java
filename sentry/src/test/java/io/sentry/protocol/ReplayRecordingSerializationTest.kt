package io.sentry.protocol

import io.sentry.ILogger
import io.sentry.JsonSerializer
import io.sentry.ReplayRecording
import io.sentry.SentryOptions
import io.sentry.protocol.SerializationUtils.deserializeJson
import io.sentry.protocol.SerializationUtils.sanitizedFile
import io.sentry.protocol.SerializationUtils.serializeToString
import io.sentry.rrweb.RRWebMetaEventSerializationTest
import io.sentry.rrweb.RRWebVideoEventSerializationTest
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringWriter
import kotlin.test.assertEquals

class ReplayRecordingSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = ReplayRecording().apply {
            segmentId = 0
            payload = listOf(
                RRWebMetaEventSerializationTest.Fixture().getSut(),
                RRWebVideoEventSerializationTest.Fixture().getSut()
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/replay_recording.json")
        val actual = serializeToString(fixture.getSut(), fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/replay_recording.json")
        val actual = deserializeJson(expectedJson, ReplayRecording.Deserializer(), fixture.logger)
        val actualJson = serializeToString(actual, fixture.logger)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun serializePayload() {
        val expected = sanitizedFile("json/replay_recording_payload.json")
        val writer = StringWriter()
        JsonSerializer(SentryOptions()).serialize(fixture.getSut().payload as Any, writer)
        val actual = writer.toString()
        assertEquals(expected, actual)
    }
}
