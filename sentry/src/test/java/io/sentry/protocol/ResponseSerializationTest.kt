package io.sentry.protocol

import io.sentry.ILogger
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class ResponseSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            Response().apply {
                cookies = "PHPSESSID=298zf09hf012fh2;csrftoken=u32t4o3tb3gg43;_gat=1;"
                headers = mapOf("content-type" to "text/html")
                statusCode = 500
                bodySize = 1000
                data =
                    mapOf(
                        "d9d709db-b666-40cc-bcbb-093bb12aad26" to "1631d0e6-96b7-4632-85f8-ef69e8bcfb16",
                    )
                unknown = mapOf("arbitrary_field" to "arbitrary")
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = SerializationUtils.sanitizedFile("json/response.json")
        val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = SerializationUtils.sanitizedFile("json/response.json")
        val actual =
            SerializationUtils.deserializeJson<Response>(
                expectedJson,
                Response.Deserializer(),
                fixture.logger,
            )
        val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)
        assertEquals(expectedJson, actualJson)
    }
}
