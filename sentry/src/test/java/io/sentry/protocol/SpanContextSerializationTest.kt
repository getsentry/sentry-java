package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SpanContext
import io.sentry.SpanDataConvention
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.TracesSamplingDecision
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SpanContextSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = SpanContext(
            SentryId("afcb46b1140ade5187c4bbb5daa804df"),
            SpanId("bf6b582d-8ce3-412b-a334-f4c5539b9602"),
            "e481581d-35a4-4e97-8a1c-b554bf49f23e",
            SpanId("c7500f2a-d4e6-4f5f-a0f4-6bb67e98d5a2"),
            TracesSamplingDecision(false)
        ).apply {
            description = "c204b6c7-9753-4d45-927d-b19789bfc9a5"
            status = SpanStatus.RESOURCE_EXHAUSTED
            origin = "auto.test.unit.spancontext"
            setTag("2a5fa3f5-7b87-487f-aaa5-84567aa73642", "4781d51a-c5af-47f2-a4ed-f030c9b3e194")
            setTag("29106d7d-7fa4-444f-9d34-b9d7510c69ab", "218c23ea-694a-497e-bf6d-e5f26f1ad7bd")
            setTag("ba9ce913-269f-4c03-882d-8ca5e6991b14", "35a74e90-8db8-4610-a411-872cbc1030ac")
            data[SpanDataConvention.THREAD_NAME] = "test"
            data[SpanDataConvention.THREAD_ID] = 10
            setData("spanContextDataKey", "spanContextDataValue")
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/span_context.json")
        val actual = serializeToString(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/span_context.json")
        val actual = deserialize(expectedJson)
        assertNull(actual.sampled)
        assertNull(actual.profileSampled)
        assertNotNull(actual.tags)
        val actualJson = serializeToString(actual)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun deserializeNullOp() {
        val expectedJson = sanitizedFile("json/span_context_null_op.json")
        val actual = deserialize(expectedJson)
        assertNull(actual.sampled)
        assertNull(actual.profileSampled)
        assertNotNull(actual.tags)
        assertEquals("", actual.operation)
    }

    // Helper

    private fun sanitizedFile(path: String): String {
        return FileFromResources.invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")
    }

    private fun serializeToString(jsonSerializable: JsonSerializable): String {
        return this.serializeToString { wrt -> jsonSerializable.serialize(wrt, fixture.logger) }
    }

    private fun serializeToString(serialize: (JsonObjectWriter) -> Unit): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        serialize(jsonWrt)
        return wrt.toString()
    }

    private fun deserialize(json: String): SpanContext {
        val reader = JsonObjectReader(StringReader(json))
        return SpanContext.Deserializer().deserialize(reader, fixture.logger)
    }
}
