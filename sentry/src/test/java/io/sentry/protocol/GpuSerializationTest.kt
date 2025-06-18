package io.sentry.protocol

import io.sentry.ILogger
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class GpuSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            Gpu().apply {
                name = "d623a6b5-e1ab-4402-931b-c06f5a43a5ae"
                id = -596576280
                vendorId = "1874778041"
                vendorName = "d732cf76-07dc-48e2-8920-96d6bfc2439d"
                memorySize = -1484004451
                apiType = "95dfc8bc-88ae-4d66-b85f-6c88ad45b80f"
                isMultiThreadedRendering = true
                version = "3f3f73c3-83a2-423a-8a6f-bb3de0d4a6ae"
                npotSupport = "e06b074a-463c-45de-a959-cbabd461d99d"
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = SerializationUtils.sanitizedFile("json/gpu.json")
        val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)

        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = SerializationUtils.sanitizedFile("json/gpu.json")
        val actual =
            SerializationUtils.deserializeJson<Gpu>(
                expectedJson,
                Gpu.Deserializer(),
                fixture.logger,
            )
        val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)

        assertEquals(expectedJson, actualJson)
    }
}
