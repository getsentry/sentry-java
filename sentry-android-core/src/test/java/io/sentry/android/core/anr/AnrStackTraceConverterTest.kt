package io.sentry.android.core.anr

import org.junit.Assert
import org.junit.Test

class AnrStackTraceConverterTest {
  @Test
  fun testConvertSimpleStackTrace() {
    val elements =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )

    val anrStackTrace = AnrStackTrace(1000, elements)
    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList()
    anrStackTraces.add(anrStackTrace)

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    Assert.assertNotNull(profile)
    Assert.assertEquals(1, profile.samples.size)
    Assert.assertEquals(2, profile.frames.size)
    Assert.assertEquals(1, profile.stacks.size)

    val frame0 = profile.frames[0]
    Assert.assertEquals("MyClass.java", frame0.filename)
    Assert.assertEquals("method1", frame0.function)
    Assert.assertEquals("com.example.MyClass", frame0.module)
    Assert.assertEquals(42, frame0.lineno)

    val frame1 = profile.frames[1]
    Assert.assertEquals("AnotherClass.java", frame1.filename)
    Assert.assertEquals("method2", frame1.function)
    Assert.assertEquals("com.example.AnotherClass", frame1.module)
    Assert.assertEquals(100, frame1.lineno)

    val stack = profile.stacks[0]
    Assert.assertEquals(2, stack.size)
    Assert.assertEquals(0, (stack[0] as Int))
    Assert.assertEquals(1, (stack[1] as Int))

    val sample = profile.samples[0]
    Assert.assertEquals(0, sample.stackId)
    Assert.assertEquals("0", sample.threadId)
    Assert.assertEquals(1.0, sample.timestamp, 0.001) // 1000ms = 1s
  }

  @Test
  fun testFrameDeduplication() {
    // Create two stack traces with duplicate frames
    val elements1 =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )

    val elements2 =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.ThirdClass", "method3", "ThirdClass.java", 200),
      )

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList()
    anrStackTraces.add(AnrStackTrace(1000, elements1))
    anrStackTraces.add(AnrStackTrace(2000, elements2))

    // Convert to profile
    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    // Should have 3 frames total (dedup removes duplicate)
    Assert.assertEquals(3, profile.frames.size)

    // First sample uses stack [0, 1]
    val stack1 = profile.stacks[0]
    Assert.assertEquals(2, stack1.size)
    Assert.assertEquals(0, (stack1[0] as Int))
    Assert.assertEquals(1, (stack1[1] as Int))

    // Second sample uses stack [0, 2] (frame 0 reused)
    val stack2 = profile.stacks[1]
    Assert.assertEquals(2, stack2.size)
    Assert.assertEquals(0, (stack2[0] as Int))
    Assert.assertEquals(2, (stack2[1] as Int))
  }

  @Test
  fun testStackDeduplication() {
    // Create two stack traces with identical frames in same order
    val elements =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList()
    anrStackTraces.add(AnrStackTrace(1000, elements))
    anrStackTraces.add(AnrStackTrace(2000, elements.clone()))

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    // Should have 2 frames and 1 stack (dedup stack)
    Assert.assertEquals(2, profile.frames.size)
    Assert.assertEquals(1, profile.stacks.size)

    // Both samples should reference the same stack
    Assert.assertEquals(0, profile.samples[0].stackId)
    Assert.assertEquals(0, profile.samples[1].stackId)
  }

  @Test
  fun testTimestampConversion() {
    val elements = arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))

    val timestampsMs = longArrayOf(1000, 1500, 5000)
    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList()

    for (ts in timestampsMs) {
      anrStackTraces.add(AnrStackTrace(ts, elements))
    }

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    Assert.assertEquals(1.0, profile.samples[0].timestamp, 0.001)
    Assert.assertEquals(1.5, profile.samples[1].timestamp, 0.001)
    Assert.assertEquals(5.0, profile.samples[2].timestamp, 0.001)
  }

  @Test
  fun testNativeMethodHandling() {
    val elements = arrayOf(StackTraceElement("java.lang.System", "doSomething", null, -2))

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList()
    anrStackTraces.add(AnrStackTrace(1000, elements))

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    val frame = profile.frames[0]
    Assert.assertTrue(frame.isNative()!!)
  }

  @Test
  fun testThreadMetadata() {
    val elements = arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList()
    anrStackTraces.add(AnrStackTrace(1000, elements))

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    val threadMetadata = profile.threadMetadata["0"]
    Assert.assertNotNull(threadMetadata)
    Assert.assertEquals("main", threadMetadata!!.name)
    Assert.assertEquals(Thread.NORM_PRIORITY, threadMetadata.priority)
  }

  @Test
  fun testEmptyStackTraceList() {
    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList()

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    Assert.assertNotNull(profile)
    Assert.assertEquals(0, profile.samples.size)
    Assert.assertEquals(0, profile.frames.size)
    Assert.assertEquals(0, profile.stacks.size)
    Assert.assertTrue(profile.threadMetadata.containsKey("0"))
  }

  @Test
  fun testSampleProperties() {
    val elements = arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList()
    anrStackTraces.add(AnrStackTrace(12345, elements))

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    val sample = profile.samples[0]
    Assert.assertEquals("0", sample.threadId)
    Assert.assertEquals(0, sample.stackId)
    Assert.assertEquals(12.345, sample.timestamp, 0.001)
  }

  @Test
  fun testInAppFrameFlag() {
    val elements = arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList()
    anrStackTraces.add(AnrStackTrace(1000, elements))

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    val frame = profile.frames[0]
    Assert.assertNull(frame.isInApp())
  }
}
