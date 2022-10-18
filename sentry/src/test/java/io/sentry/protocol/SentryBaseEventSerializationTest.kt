package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonDeserializer
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SentryBaseEvent
import io.sentry.vendor.gson.stream.JsonToken
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class SentryBaseEventSerializationTest {

    /**
     * Make subclass, as `SentryBaseEvent` initializers are protected.
     */
    class Sut : SentryBaseEvent(), JsonSerializable {
        override fun serialize(writer: JsonObjectWriter, logger: ILogger) {
            writer.beginObject()
            Serializer().serialize(this, writer, logger)
            writer.endObject()
        }

        class Deserializer : JsonDeserializer<Sut> {
            override fun deserialize(reader: JsonObjectReader, logger: ILogger): Sut {
                val sut = Sut()
                reader.beginObject()

                val baseEventDeserializer = SentryBaseEvent.Deserializer()
                do {
                    val nextName = reader.nextName()
                    baseEventDeserializer.deserializeValue(sut, nextName, reader, logger)
                } while (reader.hasNext() && reader.peek() == JsonToken.NAME)
                reader.endObject()
                return sut
            }
        }
    }

    class Fixture {
        val logger = mock<ILogger>()

        fun update(sentryBaseEvent: SentryBaseEvent) {
            sentryBaseEvent.apply {
                eventId = SentryIdSerializationTest.Fixture().getSut()
                contexts.apply {
                    setApp(AppSerializationTest.Fixture().getSut())
                    setBrowser(BrowserSerializationTest.Fixture().getSut())
                    setDevice(DeviceSerializationTest.Fixture().getSut())
                    setGpu(GpuSerializationTest.Fixture().getSut())
                    setOperatingSystem(OperatingSystemSerializationTest.Fixture().getSut())
                    setRuntime(SentryRuntimeSerializationTest.Fixture().getSut())
                    trace = SpanContextSerializationTest.Fixture().getSut()
                }
                sdk = SdkVersionSerializationTest.Fixture().getSut()
                request = RequestSerializationTest.Fixture().getSut()
                tags = mapOf(
                    "79ba41db-8dc6-4156-b53e-6cf6d742eb88" to "690ce82f-4d5d-4d81-b467-461a41dd9419"
                )
                release = "be9b8133-72f5-497b-adeb-b0a245eebad6"
                environment = "89204175-e462-4628-8acb-3a7fa8d8da7d"
                platform = "38decc78-2711-4a6a-a0be-abb61bfa5a6e"
                user = UserSerializationTest.Fixture().getSut()
                serverName = "e6f0ae04-0f40-421b-aad1-f68c15117937"
                dist = "27022a08-aace-40c6-8d0a-358a27fcaa7a"
                breadcrumbs = listOf(
                    BreadcrumbSerializationTest.Fixture().getSut()
                )
                setExtra("34a7d067-fad2-49d9-97b9-71eff243127b", "fe3dc1cf-4a99-4213-85bb-e0957b8349b8")
            }
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/sentry_base_event.json")
        val sut = Sut().apply { fixture.update(this) }
        val actual = serialize(sut)
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/sentry_base_event.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        assertEquals(expectedJson, actualJson)
    }

    // Helper

    private fun sanitizedFile(path: String): String {
        return FileFromResources.invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")
    }

    private fun serialize(jsonSerializable: JsonSerializable): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        jsonSerializable.serialize(jsonWrt, fixture.logger)
        return wrt.toString()
    }

    private fun deserialize(json: String): Sut {
        val reader = JsonObjectReader(StringReader(json))
        return Sut.Deserializer().deserialize(reader, fixture.logger)
    }
}
