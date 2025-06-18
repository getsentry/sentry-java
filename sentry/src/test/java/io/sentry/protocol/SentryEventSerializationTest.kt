package io.sentry.protocol

import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class SentryEventSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            SentryEvent(
                DateUtils.getDateTime("1942-07-09T12:55:34.000Z"),
            ).apply {
                message = MessageSerializationTest.Fixture().getSut()
                logger = "f715e1d6-4ef6-4ea3-ad77-44b5230813c3"
                threads =
                    listOf(
                        SentryThreadSerializationTest.Fixture().getSut(),
                    )
                exceptions =
                    listOf(
                        SentryExceptionSerializationTest.Fixture().getSut(),
                    )
                fingerprints =
                    listOf(
                        "ab3a347a-4cc1-4fd4-b4cf-1dc56b670c5b",
                        "340cfef9-4820-4549-ac07-c3b353c81c50",
                    )
                level = SentryLevel.ERROR
                transaction = "e7aea178-e3a6-46bc-be17-38a3ea8920b6"
                setModule("01c8a4f6-8861-4575-a10e-5ed3fba7c794", "b4083431-47e9-433a-b58f-58796f63e27c")
                contexts.apply { setSpring(SpringSerializationTest.Fixture().getSut()) }
                SentryBaseEventSerializationTest.Fixture().update(this)
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
        val expected = sanitizedFile("json/sentry_event.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/sentry_event.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        assertEquals(expectedJson, actualJson)
    }

    // Helper

    private fun sanitizedFile(path: String): String =
        FileFromResources
            .invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")

    private fun serialize(jsonSerializable: JsonSerializable): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        jsonSerializable.serialize(jsonWrt, fixture.logger)
        return wrt.toString()
    }

    private fun deserialize(json: String): SentryEvent {
        val reader = JsonObjectReader(StringReader(json))
        return SentryEvent.Deserializer().deserialize(reader, fixture.logger)
    }
}
