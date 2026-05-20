package io.sentry.android.core.internal.threaddump

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArtContextParserTest {

  @Test
  fun `parses pretty size bytes`() {
    val parser = ArtContextParser()
    parser.parseLine("Free memory 0B")
    assertEquals(0L, parser.artContext!!.freeMemory)

    val parser2 = ArtContextParser()
    parser2.parseLine("Free memory 512B")
    assertEquals(512L, parser2.artContext!!.freeMemory)
  }

  @Test
  fun `parses pretty size kilobytes`() {
    val parser = ArtContextParser()
    parser.parseLine("Free memory 3107KB")
    assertEquals(3107L * 1024, parser.artContext!!.freeMemory)
  }

  @Test
  fun `parses pretty size megabytes`() {
    val parser = ArtContextParser()
    parser.parseLine("Free memory until OOME 187MB")
    assertEquals(187L * 1024 * 1024, parser.artContext!!.freeMemoryUntilOome)
  }

  @Test
  fun `parses pretty size gigabytes`() {
    val parser = ArtContextParser()
    parser.parseLine("Max memory 2GB")
    assertEquals(2L * 1024 * 1024 * 1024, parser.artContext!!.maxMemory)
  }

  @Test
  fun `sets null for invalid pretty size`() {
    val parser = ArtContextParser()
    parser.parseLine("Free memory 100TB")
    assertNull(parser.artContext!!.freeMemory)
  }

  @Test
  fun `parses time in milliseconds`() {
    val parser = ArtContextParser()
    parser.parseLine("Total GC time: 11.807ms")
    assertEquals(11.807, parser.artContext!!.gcTotalTime)
  }

  @Test
  fun `parses time in seconds`() {
    val parser = ArtContextParser()
    parser.parseLine("Total GC time: 2.5s")
    assertEquals(2500.0, parser.artContext!!.gcTotalTime)
  }

  @Test
  fun `parses time in microseconds`() {
    val parser = ArtContextParser()
    parser.parseLine("Total GC time: 500us")
    assertEquals(0.5, parser.artContext!!.gcTotalTime)
  }

  @Test
  fun `parses time in nanoseconds`() {
    val parser = ArtContextParser()
    parser.parseLine("Total GC time: 1000000ns")
    assertEquals(1.0, parser.artContext!!.gcTotalTime)
  }

  @Test
  fun `parses zero duration`() {
    val parser = ArtContextParser()
    parser.parseLine("Total GC time: 0")
    assertEquals(0.0, parser.artContext!!.gcTotalTime)
  }

  @Test
  fun `parses all memory fields`() {
    val parser = ArtContextParser()
    parser.parseLine("Free memory 3107KB")
    parser.parseLine("Free memory until GC 3107KB")
    parser.parseLine("Free memory until OOME 187MB")
    parser.parseLine("Total memory 7592KB")
    parser.parseLine("Max memory 192MB")

    val info = parser.artContext
    assertNotNull(info)
    assertEquals(3107L * 1024, info.freeMemory)
    assertEquals(3107L * 1024, info.freeMemoryUntilGc)
    assertEquals(187L * 1024 * 1024, info.freeMemoryUntilOome)
    assertEquals(7592L * 1024, info.totalMemory)
    assertEquals(192L * 1024 * 1024, info.maxMemory)
  }

  @Test
  fun `parses all gc fields`() {
    val parser = ArtContextParser()
    parser.parseLine("Total time waiting for GC to complete: 8.054ms")
    parser.parseLine("Total GC count: 1")
    parser.parseLine("Total GC time: 11.807ms")
    parser.parseLine("Total blocking GC count: 1")
    parser.parseLine("Total blocking GC time: 11.873ms")
    parser.parseLine("Total pre-OOME GC count: 0")

    val info = parser.artContext
    assertNotNull(info)
    assertEquals(8.054, info.gcWaitingTime)
    assertEquals(1L, info.gcTotalCount)
    assertEquals(11.807, info.gcTotalTime)
    assertEquals(1L, info.gcBlockingCount)
    assertEquals(11.873, info.gcBlockingTime)
    assertEquals(0L, info.gcPreOomeCount)
  }

  @Test
  fun `ignores unrelated lines`() {
    val parser = ArtContextParser()
    parser.parseLine("some random line")
    parser.parseLine("DALVIK THREADS (29):")
    parser.parseLine("")
    assertNull(parser.artContext)
  }
}
