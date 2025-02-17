package io.sentry

import io.sentry.protocol.SentryId
import io.sentry.protocol.TransactionNameSource
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraceContextSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = TraceContext(
            SentryId("65bcd18546c942069ed957b15b4ace7c"),
            "5d593cac-f833-4845-bb23-4eabdf720da2",
            "9ee2c92c-401e-4296-b6f0-fb3b13edd9ee",
            "0666ab02-6364-4135-aa59-02e8128ce052",
            "c052c566-6619-45f5-a61f-172802afa39a",
            "0252ec25-cd0a-4230-bd2f-936a4585637e",
            "0.00000021",
            "true",
            SentryId("3367f5196c494acaae85bbbd535379aa"),
            "0.00000012"
        )
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/trace_state.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/trace_state.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `sample rate uses digit dot digit formatting`() {
        val traceContext = createTraceContext(0.00000021)

        val json = serialize(traceContext)
        assertTrue(json.contains(""""sample_rate":"0.00000021""""), json)
    }

    private fun createTraceContext(sRate: Double): TraceContext {
        val baggage = Baggage(fixture.logger)
        val scopes: IScopes = mock()
        whenever(scopes.options).thenReturn(SentryOptions())
        baggage.setValuesFromTransaction(
            SentryId(),
            SentryId(),
            SentryOptions().apply {
                dsn = dsnString
                environment = "prod"
                release = "1.0.17"
                tracesSampleRate = sRate
            },
            TracesSamplingDecision(sRate > 0.5, sRate),
            "name",
            TransactionNameSource.ROUTE
        )
        return baggage.toTraceContext()!!
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

    private fun deserialize(json: String): TraceContext {
        val reader = JsonObjectReader(StringReader(json))
        return TraceContext.Deserializer().deserialize(reader, fixture.logger)
    }
}
