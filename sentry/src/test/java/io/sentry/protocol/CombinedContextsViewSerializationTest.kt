package io.sentry.protocol

import io.sentry.CombinedContextsView
import io.sentry.ILogger
import io.sentry.JsonObjectWriter
import io.sentry.ScopeType
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class CombinedContextsViewSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut(): CombinedContextsView {
            val current = Contexts()
            val isolation = Contexts()
            val global = Contexts()
            val combined = CombinedContextsView(global, isolation, current, ScopeType.ISOLATION)

            current.setApp(AppSerializationTest.Fixture().getSut())
            current.setBrowser(BrowserSerializationTest.Fixture().getSut())
            current.setTrace(SpanContextSerializationTest.Fixture().getSut())

            isolation.setDevice(DeviceSerializationTest.Fixture().getSut())
            isolation.setOperatingSystem(OperatingSystemSerializationTest.Fixture().getSut())
            isolation.setResponse(ResponseSerializationTest.Fixture().getSut())
            isolation.setSpring(SpringSerializationTest.Fixture().getSut())

            global.setRuntime(SentryRuntimeSerializationTest.Fixture().getSut())
            global.setGpu(GpuSerializationTest.Fixture().getSut())

            return combined
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = SerializationUtils.sanitizedFile("json/contexts.json")
        val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)

        assertEquals(expected, actual)
    }

    @Test
    fun serializeUnknownEntry() {
        val sut = fixture.getSut()
        sut["fixture-key"] = "fixture-value"

        val writer = mock<JsonObjectWriter>().apply {
            whenever(name(any())).thenReturn(this)
        }
        sut.serialize(writer, fixture.logger)

        verify(writer).name("fixture-key")
        verify(writer).value(fixture.logger, "fixture-value")
    }

    @Test
    fun deserialize() {
        val expectedJson = SerializationUtils.sanitizedFile("json/contexts.json")
        val actual = SerializationUtils.deserializeJson(
            expectedJson,
            Contexts.Deserializer(),
            fixture.logger
        )
        val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun deserializeUnknownEntry() {
        val sut = fixture.getSut()
        sut["fixture-key"] = "fixture-value"
        val serialized = SerializationUtils.serializeToString(sut, fixture.logger)
        val deserialized = SerializationUtils.deserializeJson(
            serialized,
            Contexts.Deserializer(),
            fixture.logger
        )

        assertEquals("fixture-value", deserialized["fixture-key"])
    }
}
