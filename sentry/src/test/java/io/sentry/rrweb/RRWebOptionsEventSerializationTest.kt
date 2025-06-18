package io.sentry.rrweb

import io.sentry.ILogger
import io.sentry.SentryOptions
import io.sentry.SentryReplayOptions.SentryReplayQuality.LOW
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SerializationUtils
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class RRWebOptionsEventSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            RRWebOptionsEvent(
                SentryOptions().apply {
                    sdkVersion = SdkVersion("sentry.java", "7.19.1")
                    sessionReplay.sessionSampleRate = 0.5
                    sessionReplay.onErrorSampleRate = 0.1
                    sessionReplay.quality = LOW
                    sessionReplay.unmaskViewClasses.add("com.example.MyClass")
                    sessionReplay.maskViewClasses.clear()
                },
            ).apply {
                timestamp = 12345678901
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = SerializationUtils.sanitizedFile("json/rrweb_options_event.json")
        val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = SerializationUtils.sanitizedFile("json/rrweb_options_event.json")
        val actual =
            SerializationUtils.deserializeJson(expectedJson, RRWebOptionsEvent.Deserializer(), fixture.logger)
        val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)
        assertEquals(expectedJson, actualJson)
    }
}
