package io.sentry.util

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.JsonSerializer
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import java.io.Writer
import java.util.Calendar
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonSerializationUtilsTest {
    @Test
    fun `serializes calendar to map`() {
        val calendar = Calendar.getInstance()
        calendar.set(2022, 0, 1, 11, 59, 58)

        val actual = JsonSerializationUtils.calendarToMap(calendar)

        val expected =
            mapOf<String, Any?>(
                "month" to 0,
                "year" to 2022,
                "dayOfMonth" to 1,
                "hourOfDay" to 11,
                "minute" to 59,
                "second" to 58,
            )
        assertEquals(expected, actual)
    }

    @Test
    fun `serializes AtomicIntegerArray to list`() {
        val actual = JsonSerializationUtils.atomicIntegerArrayToList(AtomicIntegerArray(arrayOf(1, 2, 3).toIntArray()))
        assertEquals(listOf(1, 2, 3), actual)
    }

    @Test
    fun `returns byte array of given serializable`() {
        val mockSerializer: JsonSerializer =
            mock {
                on(it.serialize(any<JsonSerializable>(), any())).then { invocationOnMock: InvocationOnMock ->
                    val writer: Writer = invocationOnMock.getArgument(1)
                    writer.write("mock-data")
                    writer.flush()
                }
            }
        val logger: ILogger = mock()
        val serializable: JsonSerializable = mock()
        val actualBytes = JsonSerializationUtils.bytesFrom(mockSerializer, logger, serializable)

        assertContentEquals("mock-data".toByteArray(), actualBytes, "Byte array should represent the mocked input data.")
    }

    @Test
    fun `return null on serialization error`() {
        val mockSerializer: JsonSerializer =
            mock {
                on(it.serialize(any<JsonSerializable>(), any())).then {
                    throw Exception("Mocked exception.")
                }
            }
        val logger: ILogger = mock()
        val serializable: JsonSerializable = mock()
        val actualBytes = JsonSerializationUtils.bytesFrom(mockSerializer, logger, serializable)

        assertNull(actualBytes, "Mocker error should be captured and null returned.")
    }
}
