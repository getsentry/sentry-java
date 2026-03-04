package io.sentry.util

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.JsonSerializer
import io.sentry.ObjectWriter
import io.sentry.SentryLogEvent
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import java.io.Writer
import java.util.Calendar
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class JsonSerializationUtilsTest {

  private val serializer = JsonSerializer(SentryOptions())
  private val logger: ILogger = mock()

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
    val actual =
      JsonSerializationUtils.atomicIntegerArrayToList(
        AtomicIntegerArray(arrayOf(1, 2, 3).toIntArray())
      )
    assertEquals(listOf(1, 2, 3), actual)
  }

  @Test
  fun `returns byte array of given serializable`() {
    val mockSerializer: JsonSerializer = mock {
      on(it.serialize(any<JsonSerializable>(), any())).then { invocationOnMock: InvocationOnMock ->
        val writer: Writer = invocationOnMock.getArgument(1)
        writer.write("mock-data")
        writer.flush()
      }
    }
    val logger: ILogger = mock()
    val serializable: JsonSerializable = mock()
    val actualBytes = JsonSerializationUtils.bytesFrom(mockSerializer, logger, serializable)

    assertContentEquals(
      "mock-data".toByteArray(),
      actualBytes,
      "Byte array should represent the mocked input data.",
    )
  }

  @Test
  fun `return null on serialization error`() {
    val mockSerializer: JsonSerializer = mock {
      on(it.serialize(any<JsonSerializable>(), any())).then { throw Exception("Mocked exception.") }
    }
    val logger: ILogger = mock()
    val serializable: JsonSerializable = mock()
    val actualBytes = JsonSerializationUtils.bytesFrom(mockSerializer, logger, serializable)

    assertNull(actualBytes, "Mocker error should be captured and null returned.")
  }

  @Test
  fun `byteSizeOf returns same size as bytesFrom for ASCII`() {
    val logEvent = SentryLogEvent(SentryId(), 1234567890.0, "Hello ASCII", SentryLogLevel.INFO)

    val actualBytes = JsonSerializationUtils.bytesFrom(serializer, logger, logEvent)
    val byteSize = JsonSerializationUtils.byteSizeOf(serializer, logger, logEvent)

    assertEquals(
      (actualBytes?.size ?: -1).toLong(),
      byteSize,
      "byteSizeOf should match actual byte array length",
    )
    assertTrue(byteSize > 0, "byteSize should be positive")
  }

  @Test
  fun `byteSizeOf returns same size as bytesFrom for UTF-8 characters`() {
    // Mix of 1-byte, 2-byte, 3-byte and 4-byte UTF-8 characters
    val logEvent =
      SentryLogEvent(SentryId(), 1234567890.0, "Hello 世界 café 🎉 🚀", SentryLogLevel.WARN)

    val actualBytes = JsonSerializationUtils.bytesFrom(serializer, logger, logEvent)
    val byteSize = JsonSerializationUtils.byteSizeOf(serializer, logger, logEvent)

    assertEquals(
      (actualBytes?.size ?: -1).toLong(),
      byteSize,
      "byteSizeOf should match actual byte array length for UTF-8",
    )
    assertTrue(byteSize > 0, "byteSize should be positive")
  }

  @Test
  fun `byteSizeOf returns 0 on serialization error`() {
    val serializable =
      object : JsonSerializable {
        override fun serialize(writer: ObjectWriter, logger: ILogger) {
          throw RuntimeException("Serialization error")
        }
      }

    val byteSize = JsonSerializationUtils.byteSizeOf(serializer, logger, serializable)

    assertEquals(0, byteSize, "byteSizeOf should return 0 on error")
  }

  @Test
  fun `byteSizeOf returns 0 on null serializable`() {
    val byteSize = JsonSerializationUtils.byteSizeOf(serializer, logger, null)

    assertEquals(0, byteSize, "byteSizeOf should return 0 on null serializable")
  }
}
