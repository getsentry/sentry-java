package io.sentry.android.core.anr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnrStackTraceTest {

  @Test
  fun `serialize and deserialize preserves stack trace data`() {
    val stackTraceElements =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", null, 42),
        StackTraceElement("com.example.MyClass", "method1", "", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )
    val original = AnrStackTrace(1234567890L, stackTraceElements)

    val bytes = ByteArrayOutputStream()
    val dos = DataOutputStream(bytes)
    original.serialize(dos)
    dos.flush()

    val dis = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    val deserialized = AnrStackTrace.deserialize(dis)

    assertNotNull(deserialized)
    assertEquals(original.timestampMs, deserialized.timestampMs)
    assertEquals(original.stack.size, deserialized.stack.size)

    for (i in original.stack.indices) {
      assertEquals(original.stack[i].className, deserialized.stack[i].className)
      assertEquals(original.stack[i].methodName, deserialized.stack[i].methodName)
      assertEquals(original.stack[i].fileName, deserialized.stack[i].fileName)
      assertEquals(original.stack[i].lineNumber, deserialized.stack[i].lineNumber)
    }
  }

  @Test
  fun `compareTo sorts by timestamp ascending`() {
    val trace1 = AnrStackTrace(3000L, emptyArray())
    val trace2 = AnrStackTrace(1000L, emptyArray())
    val trace3 = AnrStackTrace(2000L, emptyArray())

    val list = listOf(trace3, trace1, trace2)
    val sorted = list.sorted()

    assertEquals(1000L, sorted[0].timestampMs)
    assertEquals(2000L, sorted[1].timestampMs)
    assertEquals(3000L, sorted[2].timestampMs)
  }

  @Test
  fun `serialize and deserialize handles empty stack`() {
    val original = AnrStackTrace(1234567890L, emptyArray())

    val bytes = ByteArrayOutputStream()
    val dos = DataOutputStream(bytes)
    original.serialize(dos)
    dos.flush()

    val dis = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    val deserialized = AnrStackTrace.deserialize(dis)

    assertNotNull(deserialized)
    assertEquals(0, deserialized.stack.size)
    assertEquals(original.timestampMs, deserialized.timestampMs)
  }

  @Test
  fun `serialize and deserialize handles native methods with no line number`() {
    val stackTraceElements =
      arrayOf(
        StackTraceElement("java.lang.reflect.Method", "invoke", null, -2),
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
      )
    val original = AnrStackTrace(1234567890L, stackTraceElements)

    val bytes = ByteArrayOutputStream()
    val dos = DataOutputStream(bytes)
    original.serialize(dos)
    dos.flush()

    val dis = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    val deserialized = AnrStackTrace.deserialize(dis)

    assertNotNull(deserialized)
    assertEquals(-2, deserialized.stack[0].lineNumber)
    assertNull(deserialized.stack[0].fileName)
    assertEquals(42, deserialized.stack[1].lineNumber)
  }

  @Test
  fun `deserialize returns null for oversized stack length`() {
    val bytes = ByteArrayOutputStream()
    val dos = DataOutputStream(bytes)
    dos.writeShort(1) // version
    dos.writeLong(1234567890L) // timestamp
    dos.writeInt(1001) // stackLength exceeds MAX_STACK_LENGTH
    dos.flush()

    val dis = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    val deserialized = AnrStackTrace.deserialize(dis)

    assertNull(deserialized)
  }

  @Test
  fun `deserialize returns null for negative stack length`() {
    val bytes = ByteArrayOutputStream()
    val dos = DataOutputStream(bytes)
    dos.writeShort(1) // version
    dos.writeLong(1234567890L) // timestamp
    dos.writeInt(-1) // negative stackLength
    dos.flush()

    val dis = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    val deserialized = AnrStackTrace.deserialize(dis)

    assertNull(deserialized)
  }
}
