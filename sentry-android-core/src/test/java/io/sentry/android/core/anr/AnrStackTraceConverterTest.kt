package io.sentry.android.core.anr

import org.junit.Assert
import org.junit.Test

class AnrStackTraceConverterTest {
  @Test
  fun testConvertSimpleStackTrace() {
    // Create a simple stack trace
    val elements =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )

    val anrStackTrace = AnrStackTrace(1000, elements)
    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList<AnrStackTrace?>()
    anrStackTraces.add(anrStackTrace)

    // Convert to profile
    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    // Verify profile structure
    Assert.assertNotNull(profile)
    Assert.assertEquals(1, profile.getSamples().size.toLong())
    Assert.assertEquals(2, profile.getFrames().size.toLong())
    Assert.assertEquals(1, profile.getStacks().size.toLong())

    // Verify frames
    val frame0 = profile.getFrames().get(0)
    Assert.assertEquals("MyClass.java", frame0.getFilename())
    Assert.assertEquals("method1", frame0.getFunction())
    Assert.assertEquals("com.example.MyClass", frame0.getModule())
    Assert.assertEquals(42, frame0.getLineno())

    val frame1 = profile.getFrames().get(1)
    Assert.assertEquals("AnotherClass.java", frame1.getFilename())
    Assert.assertEquals("method2", frame1.getFunction())
    Assert.assertEquals("com.example.AnotherClass", frame1.getModule())
    Assert.assertEquals(100, frame1.getLineno())

    // Verify stack
    val stack = profile.getStacks().get(0)
    Assert.assertEquals(2, stack.size.toLong())
    Assert.assertEquals(0, (stack.get(0) as Int).toLong())
    Assert.assertEquals(1, (stack.get(1) as Int).toLong())

    // Verify sample
    val sample = profile.getSamples().get(0)
    Assert.assertEquals(0, sample.getStackId().toLong())
    Assert.assertEquals("0", sample.getThreadId())
    Assert.assertEquals(1.0, sample.getTimestamp(), 0.001) // 1000ms = 1s
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

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList<AnrStackTrace?>()
    anrStackTraces.add(AnrStackTrace(1000, elements1))
    anrStackTraces.add(AnrStackTrace(2000, elements2))

    // Convert to profile
    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    // Should have 3 frames total (dedup removes duplicate)
    Assert.assertEquals(3, profile.getFrames().size.toLong())

    // First sample uses stack [0, 1]
    val stack1 = profile.getStacks().get(0)
    Assert.assertEquals(2, stack1.size.toLong())
    Assert.assertEquals(0, (stack1.get(0) as Int).toLong())
    Assert.assertEquals(1, (stack1.get(1) as Int).toLong())

    // Second sample uses stack [0, 2] (frame 0 reused)
    val stack2 = profile.getStacks().get(1)
    Assert.assertEquals(2, stack2.size.toLong())
    Assert.assertEquals(0, (stack2.get(0) as Int).toLong())
    Assert.assertEquals(2, (stack2.get(1) as Int).toLong())
  }

  @Test
  fun testStackDeduplication() {
    // Create two stack traces with identical frames in same order
    val elements =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList<AnrStackTrace?>()
    anrStackTraces.add(AnrStackTrace(1000, elements))
    anrStackTraces.add(AnrStackTrace(2000, elements.clone()))

    // Convert to profile
    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    // Should have 2 frames and 1 stack (dedup stack)
    Assert.assertEquals(2, profile.getFrames().size.toLong())
    Assert.assertEquals(1, profile.getStacks().size.toLong())

    // Both samples should reference the same stack
    Assert.assertEquals(0, profile.getSamples().get(0).getStackId().toLong())
    Assert.assertEquals(0, profile.getSamples().get(1).getStackId().toLong())
  }

  @Test
  fun testTimestampConversion() {
    val elements = arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))

    // Test various timestamps
    val timestampsMs = longArrayOf(1000, 1500, 5000)
    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList<AnrStackTrace?>()

    for (ts in timestampsMs) {
      anrStackTraces.add(AnrStackTrace(ts, elements))
    }

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    // Verify timestamps are converted from ms to seconds
    Assert.assertEquals(1.0, profile.getSamples().get(0).getTimestamp(), 0.001)
    Assert.assertEquals(1.5, profile.getSamples().get(1).getTimestamp(), 0.001)
    Assert.assertEquals(5.0, profile.getSamples().get(2).getTimestamp(), 0.001)
  }

  @Test
  fun testNativeMethodHandling() {
    // Create a native method stack trace
    val elements = arrayOf(StackTraceElement("java.lang.System", "doSomething", null, -2))

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList<AnrStackTrace?>()
    anrStackTraces.add(AnrStackTrace(1000, elements))

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    val frame = profile.getFrames().get(0)
    Assert.assertTrue(frame.isNative()!!)
  }

  @Test
  fun testThreadMetadata() {
    val elements = arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList<AnrStackTrace?>()
    anrStackTraces.add(AnrStackTrace(1000, elements))

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    // Verify thread metadata
    val threadMetadata = profile.getThreadMetadata().get("0")
    Assert.assertNotNull(threadMetadata)
    Assert.assertEquals("main", threadMetadata!!.getName())
    Assert.assertEquals(Thread.NORM_PRIORITY.toLong(), threadMetadata.getPriority().toLong())
  }

  @Test
  fun testEmptyStackTraceList() {
    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList<AnrStackTrace?>()

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    // Should return empty profile with thread metadata
    Assert.assertNotNull(profile)
    Assert.assertEquals(0, profile.getSamples().size.toLong())
    Assert.assertEquals(0, profile.getFrames().size.toLong())
    Assert.assertEquals(0, profile.getStacks().size.toLong())
    Assert.assertTrue(profile.getThreadMetadata().containsKey("0"))
  }

  @Test
  fun testSampleProperties() {
    val elements = arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList<AnrStackTrace?>()
    anrStackTraces.add(AnrStackTrace(12345, elements))

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    val sample = profile.getSamples().get(0)
    Assert.assertEquals("0", sample.getThreadId())
    Assert.assertEquals(0, sample.getStackId().toLong())
    Assert.assertEquals(12.345, sample.getTimestamp(), 0.001)
  }

  @Test
  fun testInAppFrameFlag() {
    val elements = arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))

    val anrStackTraces: MutableList<AnrStackTrace?> = ArrayList<AnrStackTrace?>()
    anrStackTraces.add(AnrStackTrace(1000, elements))

    val profile = StackTraceConverter.convert(AnrProfile(anrStackTraces))

    val frame = profile.getFrames().get(0)
    Assert.assertTrue(frame.isInApp()!!)
  }
}
