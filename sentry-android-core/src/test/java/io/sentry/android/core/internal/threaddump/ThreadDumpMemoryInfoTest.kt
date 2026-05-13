package io.sentry.android.core.internal.threaddump

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ThreadDumpMemoryInfoTest {

  @Test
  fun `parses pretty size bytes`() {
    val parser = ThreadDumpMemoryInfoParser()
    parser.parseLine("Free memory 0B")
    assertEquals(0L, parser.memoryInfo!!.freeMemoryBytes)

    val parser2 = ThreadDumpMemoryInfoParser()
    parser2.parseLine("Free memory 512B")
    assertEquals(512L, parser2.memoryInfo!!.freeMemoryBytes)
  }

  @Test
  fun `parses pretty size kilobytes`() {
    val parser = ThreadDumpMemoryInfoParser()
    parser.parseLine("Free memory 3107KB")
    assertEquals(3107L * 1024, parser.memoryInfo!!.freeMemoryBytes)
  }

  @Test
  fun `parses pretty size megabytes`() {
    val parser = ThreadDumpMemoryInfoParser()
    parser.parseLine("Free memory until OOME 187MB")
    assertEquals(187L * 1024 * 1024, parser.memoryInfo!!.freeMemoryUntilOOMEBytes)
  }

  @Test
  fun `parses pretty size gigabytes`() {
    val parser = ThreadDumpMemoryInfoParser()
    parser.parseLine("Max memory 2GB")
    assertEquals(2L * 1024 * 1024 * 1024, parser.memoryInfo!!.maxMemoryBytes)
  }

  @Test
  fun `sets null for invalid pretty size`() {
    val parser = ThreadDumpMemoryInfoParser()
    parser.parseLine("Free memory 100TB")
    assertNull(parser.memoryInfo!!.freeMemoryBytes)
  }

  @Test
  fun `parses time in milliseconds`() {
    val parser = ThreadDumpMemoryInfoParser()
    parser.parseLine("Total GC time: 11.807ms")
    assertEquals(11.807, parser.memoryInfo!!.totalGcTimeMs)
  }

  @Test
  fun `parses all memory fields`() {
    val parser = ThreadDumpMemoryInfoParser()
    parser.parseLine("Free memory 3107KB")
    parser.parseLine("Free memory until GC 3107KB")
    parser.parseLine("Free memory until OOME 187MB")
    parser.parseLine("Total memory 7592KB")
    parser.parseLine("Max memory 192MB")

    val info = parser.memoryInfo
    assertNotNull(info)
    assertEquals(3107L * 1024, info.freeMemoryBytes)
    assertEquals(3107L * 1024, info.freeMemoryUntilGcBytes)
    assertEquals(187L * 1024 * 1024, info.freeMemoryUntilOOMEBytes)
    assertEquals(7592L * 1024, info.totalMemoryBytes)
    assertEquals(192L * 1024 * 1024, info.maxMemoryBytes)
  }

  @Test
  fun `parses all gc fields`() {
    val parser = ThreadDumpMemoryInfoParser()
    parser.parseLine("Total time waiting for GC to complete: 8.054ms")
    parser.parseLine("Total GC count: 1")
    parser.parseLine("Total GC time: 11.807ms")
    parser.parseLine("Total blocking GC count: 1")
    parser.parseLine("Total blocking GC time: 11.873ms")
    parser.parseLine("Total pre-OOME GC count: 0")

    val info = parser.memoryInfo
    assertNotNull(info)
    assertEquals(8.054, info.totalTimeWaitingForGcMs)
    assertEquals(1L, info.totalGcCount)
    assertEquals(11.807, info.totalGcTimeMs)
    assertEquals(1L, info.totalBlockingGcCount)
    assertEquals(11.873, info.totalBlockingGcTimeMs)
    assertEquals(0L, info.totalPreOomeGcCount)
  }

  @Test
  fun `ignores unrelated lines`() {
    val parser = ThreadDumpMemoryInfoParser()
    parser.parseLine("some random line")
    parser.parseLine("DALVIK THREADS (29):")
    parser.parseLine("")
    assertNull(parser.memoryInfo)
  }
}
