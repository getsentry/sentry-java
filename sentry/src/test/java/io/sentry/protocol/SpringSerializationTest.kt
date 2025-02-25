package io.sentry.protocol

import io.sentry.ILogger
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class SpringSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = Spring().apply {
            activeProfiles = arrayOf("some", "profiles")
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = SerializationUtils.sanitizedFile("json/spring.json")
        val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)

        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = SerializationUtils.sanitizedFile("json/spring.json")
        val actual = SerializationUtils.deserializeJson<Spring>(
            expectedJson,
            Spring.Deserializer(),
            fixture.logger
        )
        val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)

        assertEquals(expectedJson, actualJson)
    }
}
