package io.sentry.protocol

import com.nhaarman.mockitokotlin2.mock
import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class SentryTransactionSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = SentryTransaction(
            "e54578ec-c9a8-4bce-8e3c-839e6c058fed",
            DateUtils.dateToSeconds(DateUtils.getDateTime("1999-01-17T22:56:36.000Z")),
            DateUtils.dateToSeconds(DateUtils.getDateTime("1999-02-26T00:48:44.000Z")),
            listOf(
                SentrySpanSerializationTest.Fixture().getSut()
            ),
            mapOf(
                "386384cb-1162-49e7-aea1-db913d4fca63" to MeasurementValueSerializationTest.Fixture().getSut()
            )
        ).apply {
            SentryBaseEventSerializationTest.Fixture().update(this)
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/sentry_transaction.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/sentry_transaction.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `deserialize legacy date format`() {
        val expectedJson = sanitizedFile("json/sentry_transaction_legacy_date_format.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        assertEquals(sanitizedFile("json/sentry_transaction.json"), actualJson)
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

    private fun deserialize(json: String): SentryTransaction {
        val reader = JsonObjectReader(StringReader(json))
        return SentryTransaction.Deserializer().deserialize(reader, fixture.logger)
    }
}
