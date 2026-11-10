package io.sentry.android.core.anr

import com.google.common.truth.Truth.assertThat
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

  @Test
  fun `v2 round trip keeps the thread state`() {
    val stack = arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))
    val original = AnrStackTrace(1234567890L, stack, Thread.State.BLOCKED)

    val deserialized = roundTrip(original)

    assertThat(deserialized).isNotNull()
    assertThat(deserialized!!.state).isEqualTo(Thread.State.BLOCKED)
    assertThat(deserialized.timestampMs).isEqualTo(1234567890L)
    assertThat(deserialized.stack).isEqualTo(stack)
  }

  @Test
  fun `v2 round trip keeps a null thread state`() {
    val original = AnrStackTrace(1234567890L, emptyArray(), null)

    val deserialized = roundTrip(original)

    assertThat(deserialized).isNotNull()
    assertThat(deserialized!!.state).isNull()
  }

  @Test
  fun `v1 bytes are still read with a null state`() {
    val bytes = ByteArrayOutputStream()
    val dos = DataOutputStream(bytes)
    dos.writeShort(1)
    dos.writeLong(1234567890L)
    dos.writeInt(1)
    dos.writeUTF("com.example.MyClass")
    dos.writeUTF("method1")
    dos.writeBoolean(false)
    dos.writeUTF("MyClass.java")
    dos.writeInt(42)
    dos.flush()

    val deserialized =
      AnrStackTrace.deserialize(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))

    assertThat(deserialized).isNotNull()
    assertThat(deserialized!!.state).isNull()
    assertThat(deserialized.timestampMs).isEqualTo(1234567890L)
    assertThat(deserialized.stack)
      .isEqualTo(arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42)))
  }

  @Test
  fun `unknown thread state name reads as null`() {
    val bytes = ByteArrayOutputStream()
    val dos = DataOutputStream(bytes)
    dos.writeShort(2)
    dos.writeLong(1234567890L)
    dos.writeUTF("NOT_A_STATE")
    dos.writeInt(0)
    dos.flush()

    val deserialized =
      AnrStackTrace.deserialize(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))

    assertThat(deserialized).isNotNull()
    assertThat(deserialized!!.state).isNull()
  }

  @Test
  fun `version 3 returns null`() {
    val bytes = ByteArrayOutputStream()
    val dos = DataOutputStream(bytes)
    dos.writeShort(3)
    dos.writeLong(1234567890L)
    dos.flush()

    val deserialized =
      AnrStackTrace.deserialize(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))

    assertThat(deserialized).isNull()
  }

  private fun roundTrip(original: AnrStackTrace): AnrStackTrace? {
    val bytes = ByteArrayOutputStream()
    val dos = DataOutputStream(bytes)
    original.serialize(dos)
    dos.flush()
    return AnrStackTrace.deserialize(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
  }
}
