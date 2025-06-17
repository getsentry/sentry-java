package io.sentry.protocol

import io.sentry.ILogger
import io.sentry.JsonDeserializer
import io.sentry.JsonSerializable
import io.sentry.ObjectReader
import io.sentry.ObjectWriter
import io.sentry.SentryBaseEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.vendor.gson.stream.JsonToken
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class SentryBaseEventSerializationTest {

    /**
     * Make subclass, as `SentryBaseEvent` initializers are protected.
     */
    class Sut : SentryBaseEvent(), JsonSerializable {
        override fun serialize(writer: ObjectWriter, logger: ILogger) {
            writer.beginObject()
            Serializer().serialize(this, writer, logger)
            writer.endObject()
        }

        class Deserializer : JsonDeserializer<Sut> {
            override fun deserialize(reader: ObjectReader, logger: ILogger): Sut {
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
                    setResponse(ResponseSerializationTest.Fixture().getSut())
                    setTrace(SpanContextSerializationTest.Fixture().getSut())
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
                debugMeta = DebugMetaSerializationTest.Fixture().getSut()
                setExtra("34a7d067-fad2-49d9-97b9-71eff243127b", "fe3dc1cf-4a99-4213-85bb-e0957b8349b8")
            }
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
        val expected = SerializationUtils.sanitizedFile("json/sentry_base_event.json")
        val sut = Sut().apply { fixture.update(this) }
        val actual = SerializationUtils.serializeToString(sut, fixture.logger)

        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val inputJson = SerializationUtils.sanitizedFile("json/sentry_base_event_with_null_extra.json")
        val expectedJson = SerializationUtils.sanitizedFile("json/sentry_base_event.json")
        val actual = SerializationUtils.deserializeJson(
            inputJson,
            Sut.Deserializer(),
            fixture.logger
        )
        val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)

        assertEquals(expectedJson, actualJson)
    }
}
